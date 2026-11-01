#!/usr/bin/env bash
# Reconstruit l'image et relance toute la stack en conteneurs (sans toucher aux données).
. "$(dirname "$0")/_common.sh"
load_env

info "Build Maven (sans tests)..."
./mvnw -B -q clean package -DskipTests

info "Rebuild des conteneurs..."
docker compose up --build -d
info "Stack relancée : http://localhost:${SERVER_PORT}/actuator/health"
