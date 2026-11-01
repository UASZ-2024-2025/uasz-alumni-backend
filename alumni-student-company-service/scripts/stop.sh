#!/usr/bin/env bash
# Arrête les conteneurs (les données PostgreSQL sont conservées).
. "$(dirname "$0")/_common.sh"

info "Arrêt des conteneurs..."
docker compose down --remove-orphans
info "Conteneurs arrêtés"
