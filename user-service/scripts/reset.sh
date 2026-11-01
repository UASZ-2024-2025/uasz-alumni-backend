#!/usr/bin/env bash
# DESTRUCTIF : supprime le volume PostgreSQL (toutes les données) puis relance PostgreSQL.
# À utiliser aussi après un changement de POSTGRES_PASSWORD / POSTGRES_USER dans .env.
# Usage : ./scripts/reset.sh [-y]
. "$(dirname "$0")/_common.sh"
load_env

if [ "${1:-}" != "-y" ]; then
    read -r -p "Supprimer la base de données locale ? [y/N] " answer
    [ "${answer:-N}" = "y" ] || { info "Annulé"; exit 0; }
fi

docker compose down -v --remove-orphans
docker compose up -d --wait postgres
info "Base réinitialisée (le schéma sera recréé par Flyway au prochain démarrage)"
