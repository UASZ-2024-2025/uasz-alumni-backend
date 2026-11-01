#!/usr/bin/env bash
# Démarre PostgreSQL en conteneur, puis l'application en local (profil "local").
. "$(dirname "$0")/_common.sh"
load_env

info "Démarrage de PostgreSQL..."
docker compose up -d --wait postgres

info "Démarrage de l'application (profil local)..."
SPRING_PROFILES_ACTIVE=local exec ./mvnw spring-boot:run
