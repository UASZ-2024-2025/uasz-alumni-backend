#!/usr/bin/env bash
# =============================================================================
# Commande unique SonarQube pour le monorepo Alumni UASZ.
#   ./sonar/sonar.sh up                 démarre SonarQube local (docker compose)
#   ./sonar/sonar.sh init               1re fois : mot de passe admin + token (sonar/.sonar-token)
#   ./sonar/sonar.sh scan [service|all] tests + couverture + analyse (défaut : all)
#   ./sonar/sonar.sh status | open | logs | down | reset
#
# Variables d'environnement (toutes optionnelles) :
#   SONAR_HOST_URL        serveur cible (défaut http://localhost:9000) -> serveur partagé possible
#   SONAR_TOKEN           token d'analyse (sinon lu dans sonar/.sonar-token)
#   SONAR_ADMIN_PASSWORD  mot de passe admin local défini par `init`
#   SONAR_PORT, SONAR_IMAGE, SONAR_SCANNER_MAVEN_VERSION, JACOCO_VERSION
# =============================================================================
set -euo pipefail

SONAR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SONAR_DIR")"
cd "$ROOT"

SERVICES=(user-service alumni-student-company-service job-offer-service analytics-matching-service notification-messaging-service)
SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:${SONAR_PORT:-9000}}"
SCANNER_VERSION="${SONAR_SCANNER_MAVEN_VERSION:-5.1.0.4751}"
JACOCO_VERSION="${JACOCO_VERSION:-0.8.13}"
ADMIN_PASSWORD="${SONAR_ADMIN_PASSWORD:-Alumni-Uasz-2026!}"
TOKEN_FILE="$SONAR_DIR/.sonar-token"
TOKEN_NAME="alumni-local"
COMPOSE=(docker compose -f "$SONAR_DIR/docker-compose.yml")

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31mErreur :\033[0m %s\n' "$*" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "'$1' est requis mais introuvable"; }

is_up() { curl -fsS "$SONAR_HOST_URL/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; }

wait_ready() {
  info "Attente de SonarQube ($SONAR_HOST_URL), le premier démarrage prend 1 à 2 minutes..."
  local i
  for i in $(seq 1 120); do
    if is_up; then info "SonarQube est prêt."; return 0; fi
    sleep 3
  done
  fail "SonarQube ne répond pas (voir : ./sonar/sonar.sh logs)"
}

cmd_up() {
  require docker; require curl
  "${COMPOSE[@]}" up -d
  wait_ready
  echo "Étape suivante : ./sonar/sonar.sh init   (une seule fois), puis ./sonar/sonar.sh scan"
}

cmd_init() {
  require curl
  is_up || fail "SonarQube ne répond pas sur $SONAR_HOST_URL : lancez d'abord ./sonar/sonar.sh up"

  # 1. Mot de passe admin (admin/admin par défaut à la première installation)
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' -u admin:admin -X POST "$SONAR_HOST_URL/api/users/change_password" \
    --data-urlencode "login=admin" --data-urlencode "previousPassword=admin" \
    --data-urlencode "password=$ADMIN_PASSWORD" || true)"
  [ "$code" = "204" ] && info "Mot de passe admin défini."

  if ! curl -fsS -u "admin:$ADMIN_PASSWORD" "$SONAR_HOST_URL/api/authentication/validate" | grep -q '"valid":true'; then
    fail "Connexion admin impossible. Le mot de passe a déjà été changé : exportez SONAR_ADMIN_PASSWORD, ou ./sonar/sonar.sh reset"
  fi

  # 2. Token d'analyse (réutilisé s'il est encore valide)
  if [ -s "$TOKEN_FILE" ] && curl -fsS -u "$(cat "$TOKEN_FILE"):" "$SONAR_HOST_URL/api/authentication/validate" | grep -q '"valid":true'; then
    info "Token existant toujours valide ($TOKEN_FILE)."
    return 0
  fi
  curl -s -o /dev/null -u "admin:$ADMIN_PASSWORD" -X POST "$SONAR_HOST_URL/api/user_tokens/revoke" --data-urlencode "name=$TOKEN_NAME" || true
  local resp token
  resp="$(curl -fsS -u "admin:$ADMIN_PASSWORD" -X POST "$SONAR_HOST_URL/api/user_tokens/generate" \
    --data-urlencode "name=$TOKEN_NAME" --data-urlencode "type=GLOBAL_ANALYSIS_TOKEN")"
  token="$(printf '%s' "$resp" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
  [ -n "$token" ] || fail "Génération du token impossible : $resp"
  ( umask 077; printf '%s' "$token" > "$TOKEN_FILE" )
  info "Token enregistré dans sonar/.sonar-token (ignoré par git)."
  echo "Tableau de bord : $SONAR_HOST_URL  (login : admin / mot de passe : \$SONAR_ADMIN_PASSWORD ou valeur par défaut du script)"
}

resolve_token() {
  if [ -n "${SONAR_TOKEN:-}" ]; then return 0; fi
  if [ -s "$TOKEN_FILE" ]; then SONAR_TOKEN="$(cat "$TOKEN_FILE")"; export SONAR_TOKEN; return 0; fi
  fail "Aucun token : lancez ./sonar/sonar.sh init, ou exportez SONAR_TOKEN"
}

scan_one() {
  local svc="$1" key="alumni-uasz_$1"
  local props=() line
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in ''|'#'*) continue ;; esac
    props+=("-D$line")
  done < "$SONAR_DIR/sonar.properties"

  info "Analyse de $svc  (clé : $key)"
  (
    cd "$svc"
    ./mvnw -B -ntp \
      "org.jacoco:jacoco-maven-plugin:$JACOCO_VERSION:prepare-agent" \
      test \
      "org.jacoco:jacoco-maven-plugin:$JACOCO_VERSION:report" \
      "org.sonarsource.scanner.maven:sonar-maven-plugin:$SCANNER_VERSION:sonar" \
      "${props[@]}" \
      "-Dsonar.projectKey=$key" \
      "-Dsonar.projectName=$svc" \
      "-Dsonar.host.url=$SONAR_HOST_URL"
  )
}

cmd_scan() {
  require curl
  local target="${1:-all}" list=()
  if [ "$target" = "all" ]; then
    list=("${SERVICES[@]}")
  else
    printf '%s\n' "${SERVICES[@]}" | grep -qx "$target" || fail "service inconnu : '$target' (attendu : all ${SERVICES[*]})"
    list=("$target")
  fi
  is_up || fail "SonarQube ne répond pas sur $SONAR_HOST_URL (./sonar/sonar.sh up)"
  resolve_token

  local results=() s
  for s in "${list[@]}"; do
    if scan_one "$s"; then results+=("OK    $s  -> $SONAR_HOST_URL/dashboard?id=alumni-uasz_$s")
    else results+=("ECHEC $s"); fi
  done
  printf '\n\033[1;34m==> Résultat\033[0m\n'
  printf '  %s\n' "${results[@]}"
  if printf '%s\n' "${results[@]}" | grep -q '^ECHEC'; then exit 1; fi
}

cmd_status() { if is_up; then echo "SonarQube UP sur $SONAR_HOST_URL"; else echo "SonarQube injoignable sur $SONAR_HOST_URL"; exit 1; fi; }
cmd_open()   { if command -v xdg-open >/dev/null 2>&1; then xdg-open "$SONAR_HOST_URL"; elif command -v open >/dev/null 2>&1; then open "$SONAR_HOST_URL"; else echo "$SONAR_HOST_URL"; fi; }
cmd_logs()   { "${COMPOSE[@]}" logs -f sonarqube; }
cmd_down()   { "${COMPOSE[@]}" down; }
cmd_reset()  {
  read -r -p "DESTRUCTIF : supprimer SonarQube et toutes ses analyses locales ? [y/N] " a
  [ "${a:-N}" = "y" ] || { info "Annulé"; exit 0; }
  "${COMPOSE[@]}" down -v --remove-orphans
  rm -f "$TOKEN_FILE"
  info "Instance supprimée. Relancez : up puis init."
}

usage() { sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

case "${1:-help}" in
  up)     cmd_up ;;
  init)   cmd_init ;;
  scan)   shift; cmd_scan "${1:-all}" ;;
  status) cmd_status ;;
  open)   cmd_open ;;
  logs)   cmd_logs ;;
  down)   cmd_down ;;
  reset)  cmd_reset ;;
  *)      usage ;;
esac
