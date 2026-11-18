#!/usr/bin/env bash
# Restaure une sauvegarde produite par backup.sh (fichier local ou s3://...).
# Sert aussi à la MIGRATION : sauvegarde sur l'ancienne machine, restauration sur la nouvelle.
#
#   restore.sh /opt/alumni/backups/alumni-20261118-023000.sql.gz
#   restore.sh s3://<bucket>/alumni-20261118-023000.sql.gz
#
# Les services sont arrêtés pendant la restauration (les bases sont recréées), puis relancés.
set -euo pipefail
export PATH="$PATH:/snap/bin"

SRC=${1:?usage : restore.sh <fichier.sql.gz | s3://...>}
ROOT=${ALUMNI_ROOT:-/opt/alumni}
set -a; . "$ROOT/demo.env"; set +a
COMPOSE=(docker compose --project-directory "$ROOT/app/deploy/demo" -f "$ROOT/app/deploy/demo/compose.yml"
         --env-file "$ROOT/demo.env" --env-file "$ROOT/tags.env")

if [[ "$SRC" == s3://* ]]; then
  tmp=$(mktemp --suffix=.sql.gz); trap 'rm -f "$tmp"' EXIT
  aws s3 cp --only-show-errors "$SRC" "$tmp"; SRC="$tmp"
fi
gzip -t "$SRC"

echo "ATTENTION : les données actuelles vont être remplacées par $1"
if [ -t 0 ]; then read -r -p "Continuer ? (oui/non) " ok; [ "$ok" = oui ] || exit 1; fi

# Arrêt des services applicatifs locaux (la base reste démarrée)
services=$("${COMPOSE[@]}" config --services | grep -- '-service$' || true)
[ -z "$services" ] || "${COMPOSE[@]}" stop $services
"${COMPOSE[@]}" up -d --wait db

# Coupe les connexions restantes (services de vm-b : les arrêter aussi avant, sinon ils se reconnectent)
"${COMPOSE[@]}" exec -T db psql -q -U postgres -d postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname <> 'postgres' AND pid <> pg_backend_pid();" >/dev/null

# Les erreurs « role postgres existe déjà / ne peut pas être supprimé » sont normales.
gunzip -c "$SRC" | "${COMPOSE[@]}" exec -T db psql -q -U postgres -d postgres >/dev/null

# Les mots de passe des utilisateurs sont ceux du demo.env de CETTE machine
"${COMPOSE[@]}" exec -T db psql -q -U postgres -d postgres \
  -v p1="$USER_DB_PASSWORD" -v p2="$ALUMNI_STUDENT_COMPANY_DB_PASSWORD" -v p3="$JOB_OFFER_DB_PASSWORD" \
  -v p4="$ANALYTICS_MATCHING_DB_PASSWORD" -v p5="$NOTIFICATION_MESSAGING_DB_PASSWORD" <<'SQL'
ALTER ROLE user_svc PASSWORD :'p1';
ALTER ROLE alumni_student_company_svc PASSWORD :'p2';
ALTER ROLE job_offer_svc PASSWORD :'p3';
ALTER ROLE analytics_matching_svc PASSWORD :'p4';
ALTER ROLE notification_messaging_svc PASSWORD :'p5';
SQL

"$ROOT/app/deploy/demo/scripts/deploy.sh"
echo "restauration terminée"
