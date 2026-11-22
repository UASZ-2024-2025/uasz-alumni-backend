#!/usr/bin/env bash
# Test de conformité de user-service à son contrat OpenAPI (ALUM-74).
# Prérequis : service lancé (make up) et schemathesis installé.
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS2_ARG_CONV_EXCL='*'

BASE="${BASE_URL:-http://localhost:18081/v1}"
CONTRACT="${CONTRACT:-../contracts/user-service.yaml}"
FAILED=0

env_var() { grep "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r'; }
ADMIN_EMAIL="${BOOTSTRAP_ADMIN_EMAIL:-$(env_var BOOTSTRAP_ADMIN_EMAIL)}"
ADMIN_PASSWORD="${BOOTSTRAP_ADMIN_PASSWORD:-$(env_var BOOTSTRAP_ADMIN_PASSWORD)}"

TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
[ "${#TOKEN}" -gt 200 ] || { echo "ECHEC : connexion admin impossible"; exit 1; }

expect() { # description, code attendu, args curl...
  local desc="$1" want="$2"; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if [ "$got" = "$want" ]; then echo "OK    $desc ($got)"
  else echo "ECHEC $desc : attendu $want, reçu $got"; FAILED=1; fi
}

UUID="00000000-0000-4000-8000-000000000000"
JSON=(-H "Content-Type: application/json")
AUTH=(-H "Authorization: Bearer $TOKEN")

echo "== Refus ciblés =="
expect "401 sans jeton"               401 "$BASE/users"
expect "401 jeton invalide"           401 -H "Authorization: Bearer abc" "$BASE/users"
expect "403 jeton admin sur /internal" 403 -X PUT "${AUTH[@]}" "${JSON[@]}" -d '{}' "$BASE/internal/users/$UUID/company"
expect "400 email vide"               400 -X POST "${JSON[@]}" -d '{"email":""}' "$BASE/auth/forgot-password"
expect "400 corps JSON illisible"     400 -X POST "${JSON[@]}" -d '{' "$BASE/auth/login"
expect "400 paramètre invalide"       400 "${AUTH[@]}" "$BASE/users?status=AAA"
expect "404 utilisateur inconnu"      404 "${AUTH[@]}" "$BASE/users/$UUID"

echo "== Conformité au contrat (Schemathesis) =="
schemathesis run "$CONTRACT" --url "$BASE" -H "Authorization: Bearer $TOKEN" \
  --max-examples 20 \
  --exclude-checks positive_data_acceptance,unsupported_method,negative_data_rejection \
  --exclude-method DELETE --exclude-path '/users/{id}' || FAILED=1

[ "$FAILED" -eq 0 ] && echo "CONFORME" || { echo "NON CONFORME"; exit 1; }