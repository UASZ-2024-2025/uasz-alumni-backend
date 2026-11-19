#!/usr/bin/env bash
# Déploie (ou met à jour) la démo sur CETTE machine, puis vérifie que tout est sain.
# En cas d'échec : retour automatique aux versions précédentes.
#
#   deploy.sh [--ref <commit>] [<composant>=<tag> ...]
#
#   --ref        commit du dépôt backend à utiliser pour compose.yml / Caddyfile
#   composant    user-service, alumni-student-company-service, job-offer-service,
#                analytics-matching-service, notification-messaging-service, frontend
#
# Exemples :
#   deploy.sh --ref 3f2a1c9 user-service=3f2a1c9      (après une fusion backend)
#   deploy.sh frontend=8b7e6d5                         (après une fusion frontend)
#   deploy.sh                                          (relance avec les versions actuelles)
#
# Appelé par GitHub Actions (via AWS SSM sur la démo), utilisable tel quel sur tout serveur.
set -euo pipefail

ROOT=${ALUMNI_ROOT:-/opt/alumni}
APP="$ROOT/app"
ENV_FILE="$ROOT/demo.env"
TAGS_FILE="$ROOT/tags.env"
MAIL_FILE="$ROOT/mail.env"
COMPOSE=(docker compose --project-directory "$APP/deploy/demo" -f "$APP/deploy/demo/compose.yml"
         --env-file "$ENV_FILE" --env-file "$MAIL_FILE" --env-file "$TAGS_FILE")
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-420}

log() { printf '[deploy %s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }

REF=""
UPDATES=()
while [ $# -gt 0 ]; do
  case "$1" in
    --ref) REF="$2"; shift 2 ;;
    *=*)   UPDATES+=("$1"); shift ;;
    *)     echo "argument inconnu : $1" >&2; exit 2 ;;
  esac
done

[ -f "$ENV_FILE" ] || { echo "$ENV_FILE absent : lancer d'abord bootstrap.sh" >&2; exit 1; }
touch "$TAGS_FILE" "$MAIL_FILE"
chmod 600 "$MAIL_FILE"

# Un seul déploiement à la fois sur la machine
exec 9>"$ROOT/.deploy.lock"
flock -w 600 9 || { echo "un autre déploiement est en cours" >&2; exit 1; }

PREV_REF=$(git -C "$APP" rev-parse HEAD)
cp "$TAGS_FILE" "$TAGS_FILE.prev"

rollback() {
  log "ÉCHEC : retour à la version précédente ($PREV_REF)"
  cp "$TAGS_FILE.prev" "$TAGS_FILE"
  # Le dépôt n'est remis en place que si ce déploiement l'avait changé (--ref)
  [ -z "$REF" ] || git -C "$APP" checkout -q --detach "$PREV_REF"
  "${COMPOSE[@]}" up -d --remove-orphans || true
  exit 1
}

# 1. Fichiers de déploiement au bon commit
if [ -n "$REF" ]; then
  log "dépôt -> $REF"
  git -C "$APP" fetch -q origin
  # Seul du code déjà fusionné (donc relu et validé par la CI) peut être déployé
  BRANCH=${DEPLOY_BRANCH:-develop}
  git -C "$APP" merge-base --is-ancestor "$REF" "origin/$BRANCH" \
    || { echo "refusé : $REF n'est pas dans origin/$BRANCH" >&2; exit 1; }
  git -C "$APP" checkout -q --detach "$REF"
fi

# 1b. SMTP réel (Brevo...) : si MAIL_SSM_PARAM est défini dans demo.env, mail.env est relu à chaque
#     déploiement depuis ce paramètre SecureString (lignes MAIL_*=...). Paramètre absent -> Mailpit.
MAIL_PARAM=$(sed -n 's/^MAIL_SSM_PARAM=//p' "$ENV_FILE" | tail -1)
if [ -n "$MAIL_PARAM" ]; then
  AWS_BIN=$(command -v aws || echo /snap/bin/aws)   # snap : hors du PATH des commandes SSM
  REGION=$(sed -n 's/^AWS_DEFAULT_REGION=//p' "$ENV_FILE" | tail -1)
  if out=$("$AWS_BIN" ssm get-parameter --name "$MAIL_PARAM" --with-decryption ${REGION:+--region "$REGION"} \
             --query Parameter.Value --output text 2>&1); then
    printf '%s\n' "$out" | tr -d '\r' | grep -E '^MAIL_(HOST|PORT|USERNAME|PASSWORD|FROM|STARTTLS)=' \
      > "$MAIL_FILE.new" || true
    log "e-mails : SMTP $(sed -n 's/^MAIL_HOST=//p' "$MAIL_FILE.new")"
  elif echo "$out" | grep -q ParameterNotFound; then
    : > "$MAIL_FILE.new"; log "e-mails : Mailpit ($MAIL_PARAM absent)"
  else
    cp "$MAIL_FILE" "$MAIL_FILE.new"; log "e-mails : $MAIL_PARAM illisible, réglage actuel conservé"
  fi
  install -m 600 "$MAIL_FILE.new" "$MAIL_FILE" && rm -f "$MAIL_FILE.new"
else
  # Jamais silencieux : on sait toujours d'où viennent les réglages SMTP
  log "e-mails : $(sed -n 's/^MAIL_HOST=//p' "$ENV_FILE" | tail -1 | grep . || echo Mailpit) (MAIL_SSM_PARAM non défini dans demo.env)"
fi

# 2. Versions des images (USER_SERVICE_TAG=..., FRONTEND_TAG=...)
for u in "${UPDATES[@]}"; do
  comp=${u%%=*}; tag=${u#*=}
  case "$comp" in
    user-service|alumni-student-company-service|job-offer-service|analytics-matching-service|notification-messaging-service|frontend) ;;
    *) echo "composant inconnu : $comp" >&2; exit 2 ;;
  esac
  key="$(echo "$comp" | tr 'a-z-' 'A-Z_')_TAG"
  sed -i "/^$key=/d" "$TAGS_FILE"
  echo "$key=$tag" >> "$TAGS_FILE"
  log "$comp -> $tag"
done

# 3. Téléchargement puis démarrage (seuls les conteneurs modifiés sont recréés)
# (une image introuvable fait échouer « up » juste après, ce qui déclenche le retour arrière)
"${COMPOSE[@]}" pull --quiet --ignore-pull-failures
"${COMPOSE[@]}" up -d --remove-orphans || rollback

# 4. Attente : tous les conteneurs avec healthcheck doivent être « healthy »
log "attente des services (max ${HEALTH_TIMEOUT}s)"
deadline=$((SECONDS + HEALTH_TIMEOUT))
while :; do
  pending=0; failed=""
  for id in $("${COMPOSE[@]}" ps -a -q); do
    status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id")
    name=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.service"}}' "$id")
    case "$status" in
      healthy|running) ;;
      starting) pending=1 ;;
      *) failed="$failed $name($status)" ;;
    esac
  done
  [ -z "$failed" ] && [ $pending -eq 0 ] && break
  if [ -n "$failed" ] || [ $SECONDS -ge $deadline ]; then
    log "services en échec :${failed:- délai dépassé}"
    "${COMPOSE[@]}" ps
    rollback
  fi
  sleep 5
done

"${COMPOSE[@]}" ps --format 'table {{.Service}}\t{{.Status}}'
# Images inutilisées depuis plus d'une semaine
docker image prune -af --filter "until=168h" >/dev/null 2>&1 || true
log "OK"
