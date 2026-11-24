#!/usr/bin/env bash
# Sauvegarde complète de PostgreSQL (les 5 bases + les rôles), compressée.
# 7 jours conservés en local ; copie vers S3 si BACKUP_BUCKET est défini dans demo.env.
# Lancé chaque nuit par cron (installé par bootstrap.sh) ; utilisable à la main à tout moment.
set -euo pipefail
export PATH="$PATH:/snap/bin"

ROOT=${ALUMNI_ROOT:-/opt/alumni}
set -a; . "$ROOT/demo.env"; set +a
DEST="$ROOT/backups"
FILE="$DEST/alumni-$(date -u +%Y%m%d-%H%M%S).sql.gz"
mkdir -p "$DEST"

docker compose --project-directory "$ROOT/app/deploy/demo" -f "$ROOT/app/deploy/demo/compose.yml" \
  --env-file "$ROOT/demo.env" --env-file "$ROOT/tags.env" \
  exec -T db pg_dumpall -U postgres --clean --if-exists | gzip -9 > "$FILE.part"
mv "$FILE.part" "$FILE"
chmod 600 "$FILE"
echo "sauvegarde : $FILE ($(du -h "$FILE" | cut -f1))"

if [ -n "${BACKUP_BUCKET:-}" ]; then
  aws s3 cp --only-show-errors "$FILE" "s3://$BACKUP_BUCKET/$(basename "$FILE")"
  echo "copie : s3://$BACKUP_BUCKET/$(basename "$FILE")"
fi

find "$DEST" -name 'alumni-*.sql.gz' -mtime +7 -delete
