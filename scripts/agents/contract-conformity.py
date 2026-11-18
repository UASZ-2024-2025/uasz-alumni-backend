#!/usr/bin/env python3
"""Revue de conformité contrats OpenAPI <-> code (ALUM-171, expérience 1).

Pour chaque microservice, compare son contrat (contracts/<service>.yaml) au code :
  1. couverture : chaque operationId du contrat est-il implémenté, ou encore un squelette
     (NotImplementedYetException -> 501) ?
  2. version : le SPI utilisé par le service (alumni.spi.version du pom) correspond-il à la version
     du contrat, et ce SPI est-il bien publié dans spis/repo ?
  3. codes HTTP : les statuts renvoyés par le code (HttpStatus.X) sont-ils déclarés dans le contrat ?
     (heuristique au niveau du service, pas de l'opération : à confirmer à la main)

Usage (depuis la racine du dépôt) :
    python scripts/agents/contract-conformity.py            # rapport Markdown sur la sortie standard
    python scripts/agents/contract-conformity.py --strict   # code de sortie 1 si une dérive de version est trouvée

Aucune dépendance hors PyYAML.
"""
from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
SERVICES = [
    "user-service",
    "alumni-student-company-service",
    "job-offer-service",
    "analytics-matching-service",
    "notification-messaging-service",
]
HTTP_METHODS = {"get", "post", "put", "patch", "delete"}

# HttpStatus.NAME -> code
STATUS_CODES = {
    "OK": 200, "CREATED": 201, "ACCEPTED": 202, "NO_CONTENT": 204,
    "BAD_REQUEST": 400, "UNAUTHORIZED": 401, "FORBIDDEN": 403, "NOT_FOUND": 404, "CONFLICT": 409,
    "GONE": 410, "PAYLOAD_TOO_LARGE": 413, "UNSUPPORTED_MEDIA_TYPE": 415, "UNPROCESSABLE_ENTITY": 422,
    "LOCKED": 423, "TOO_MANY_REQUESTS": 429, "INTERNAL_SERVER_ERROR": 500, "NOT_IMPLEMENTED": 501,
    "SERVICE_UNAVAILABLE": 503,
}
# Codes posés par le framework ou les squelettes : pas attendus dans chaque contrat.
FRAMEWORK_CODES = {500, 501}


@dataclass
class Operation:
    operation_id: str
    method: str
    path: str
    declared: set[int]
    status: str = "absente du code"


@dataclass
class ServiceReport:
    name: str
    contract_version: str
    pom_spi_version: str | None
    published_spi_versions: list[str]
    operations: list[Operation] = field(default_factory=list)
    undeclared_codes: dict[int, list[str]] = field(default_factory=dict)

    @property
    def version_drift(self) -> list[str]:
        problems = []
        if self.pom_spi_version is None:
            problems.append("version du SPI introuvable dans le pom")
            return problems
        if self.pom_spi_version not in self.published_spi_versions:
            problems.append(f"SPI {self.pom_spi_version} utilisé par le pom mais non publié dans spis/repo")
        if self.contract_version != self.pom_spi_version:
            problems.append(
                f"contrat en {self.contract_version}, service compilé contre le SPI {self.pom_spi_version}")
        return problems


def load_operations(contract: dict) -> list[Operation]:
    ops = []
    for path, item in (contract.get("paths") or {}).items():
        for method, spec in item.items():
            if method not in HTTP_METHODS or not isinstance(spec, dict):
                continue
            declared = {int(c) for c in (spec.get("responses") or {}) if str(c).isdigit()}
            ops.append(Operation(spec.get("operationId", f"{method} {path}"), method.upper(), path, declared))
    return ops


def method_bodies(java_sources: list[Path]) -> dict[str, str]:
    """operationId -> corps de la méthode de contrôleur qui l'implémente (méthodes @Override)."""
    bodies: dict[str, str] = {}
    signature = re.compile(r"public\s+ResponseEntity<[^>]*(?:<[^>]*>)?[^>]*>\s+(\w+)\s*\(")
    for src in java_sources:
        text = src.read_text(encoding="utf-8")
        for match in signature.finditer(text):
            name = match.group(1)
            start = text.find("{", match.end())
            depth, i = 0, start
            while i < len(text):
                if text[i] == "{":
                    depth += 1
                elif text[i] == "}":
                    depth -= 1
                    if depth == 0:
                        break
                i += 1
            bodies[name] = text[start:i + 1]
    return bodies


def pom_spi_version(service: str) -> str | None:
    pom = (ROOT / service / "pom.xml").read_text(encoding="utf-8")
    artifact = f"{service}-spi"
    dep = re.search(rf"<artifactId>{re.escape(artifact)}</artifactId>\s*<version>([^<]+)</version>", pom)
    if not dep:
        return None
    version = dep.group(1).strip()
    prop = re.fullmatch(r"\$\{([^}]+)\}", version)
    if prop:
        value = re.search(rf"<{re.escape(prop.group(1))}>([^<]+)</{re.escape(prop.group(1))}>", pom)
        return value.group(1).strip() if value else None
    return version


def published_versions(service: str) -> list[str]:
    repo = ROOT / "spis" / "repo" / "com" / "uasz" / "alumni" / f"{service}-spi"
    return sorted(p.name for p in repo.iterdir() if p.is_dir()) if repo.exists() else []


def analyse(service: str) -> ServiceReport:
    contract = yaml.safe_load((ROOT / "contracts" / f"{service}.yaml").read_text(encoding="utf-8"))
    report = ServiceReport(service, str(contract["info"]["version"]), pom_spi_version(service),
                           published_versions(service), load_operations(contract))

    main = ROOT / service / "src" / "main" / "java"
    sources = list(main.rglob("*.java"))
    controllers = [s for s in sources if "/controller/" in s.as_posix()]
    bodies = method_bodies(controllers)
    for op in report.operations:
        body = bodies.get(op.operation_id)
        if body is None:
            op.status = "absente du code"
        elif "NotImplementedYetException" in body:
            op.status = "squelette (501)"
        else:
            op.status = "implémentée"

    declared_anywhere = set().union(*(op.declared for op in report.operations)) if report.operations else set()
    status_ref = re.compile(r"HttpStatus\.([A-Z_]+)")
    for src in sources:
        for name in set(status_ref.findall(src.read_text(encoding="utf-8"))):
            code = STATUS_CODES.get(name)
            if code and code not in declared_anywhere and code not in FRAMEWORK_CODES:
                report.undeclared_codes.setdefault(code, []).append(src.relative_to(ROOT / service).as_posix())
    return report


def render(reports: list[ServiceReport]) -> str:
    out = ["# Conformité contrats OpenAPI <-> code", ""]
    out.append("| Service | Contrat | SPI du pom | Opérations | Implémentées | Squelettes (501) | Absentes | Dérive |")
    out.append("| --- | --- | --- | ---: | ---: | ---: | ---: | --- |")
    for r in reports:
        counts = {s: sum(1 for o in r.operations if o.status == s)
                  for s in ("implémentée", "squelette (501)", "absente du code")}
        drift = "; ".join(r.version_drift) or "aucune"
        out.append(f"| {r.name} | {r.contract_version} | {r.pom_spi_version} | {len(r.operations)} | "
                   f"{counts['implémentée']} | {counts['squelette (501)']} | {counts['absente du code']} | {drift} |")
    for r in reports:
        out += ["", f"## {r.name}", ""]
        implemented = [o for o in r.operations if o.status == "implémentée"]
        missing = [o for o in r.operations if o.status == "absente du code"]
        if implemented:
            out.append("Implémentées : " + ", ".join(f"`{o.operation_id}`" for o in implemented))
        if missing:
            out.append("")
            out.append("Absentes du code (aucune méthode de contrôleur trouvée) : "
                       + ", ".join(f"`{o.operation_id}`" for o in missing))
        if r.undeclared_codes:
            out.append("")
            out.append("Codes HTTP renvoyés par le code mais déclarés nulle part dans le contrat (à vérifier) :")
            for code, files in sorted(r.undeclared_codes.items()):
                out.append(f"- **{code}** : " + ", ".join(f"`{f}`" for f in sorted(set(files))))
    return "\n".join(out) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--strict", action="store_true", help="échoue (code 1) en cas de dérive de version")
    args = parser.parse_args()
    reports = [analyse(s) for s in SERVICES if (ROOT / "contracts" / f"{s}.yaml").exists()]
    sys.stdout.reconfigure(encoding="utf-8")
    print(render(reports), end="")
    if args.strict and any(r.version_drift for r in reports):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
