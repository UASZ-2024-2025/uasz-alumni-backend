#!/usr/bin/env bash
# Génère k8s/overlays/dev/{db,app}.env à partir de .env (fichiers ignorés par git).
# kustomize les lit pour créer les Secrets : plus aucun secret dans les yml.
. "$(dirname "$0")/_common.sh"
load_env

D=k8s/overlays/dev
umask 077
cat > "$D/db.env" <<EOF
POSTGRES_USER=${POSTGRES_USER}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
EOF
cat > "$D/app.env" <<EOF
JWT_SECRET=${JWT_SECRET}
EOF
info "Écrit : $D/db.env et $D/app.env"
info "Déploiement : ../alumni-uasz-infra/scripts/deploy-service.sh analytics-matching-service"
