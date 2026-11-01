#!/usr/bin/env bash
# Génère les SPIs depuis /contracts et les PUBLIE dans spis/repo (dépôt Maven versionné dans Git).
#   ./spis/build.sh                          -> tous les SPIs
#   ./spis/build.sh user-service             -> un seul SPI (+ le pom parent)
# À lancer uniquement quand un contrat change, puis committer spis/repo.
set -euo pipefail
cd "$(dirname "$0")"

SERVICES=(user-service alumni-student-company-service job-offer-service analytics-matching-service notification-messaging-service)

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mErreur :\033[0m %s\n' "$*" >&2; exit 1; }

[ -f ./mvnw ] || fail "spis/mvnw absent (cp -r user-service/.mvn spis/ && cp user-service/mvnw* spis/)"
command -v java >/dev/null 2>&1 || fail "'java' (21+) est requis"

ARGS=(-B -ntp -f pom.xml)
TARGET="${1:-all}"
if [ "$TARGET" = "all" ]; then
  WHAT="tous les SPIs"
else
  printf '%s\n' "${SERVICES[@]}" | grep -qx "$TARGET" || fail "service inconnu : '$TARGET' (attendu : ${SERVICES[*]})"
  [ -f "../contracts/$TARGET.yaml" ] || fail "contrat introuvable : contracts/$TARGET.yaml"
  ARGS+=(-pl "$TARGET-spi" -am)
  WHAT="$TARGET-spi"
fi

info "Génération + publication dans spis/repo : $WHAT"
sh ./mvnw "${ARGS[@]}" clean deploy -DskipTests -Dmaven.install.skip=true

info "Terminé. Jars publiés :"
find repo -name '*.jar' | sort
info "Étape suivante : git add spis && git commit && git push"
