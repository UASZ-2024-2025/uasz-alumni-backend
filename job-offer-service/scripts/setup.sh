#!/usr/bin/env bash
# Première installation : vérifie les outils, crée .env (mot de passe aléatoire, secret JWT).
. "$(dirname "$0")/_common.sh"

require docker
require java
require openssl

JAVA_MAJOR=$(java -version 2>&1 | awk -F[\".] '/version/ {print ($2=="1") ? $3 : $2; exit}')
[ "${JAVA_MAJOR:-0}" -ge 21 ] || fail "Java 21+ requis (détecté : ${JAVA_MAJOR:-inconnu})"

./scripts/gen-env.sh
info "Terminé. Suite : make run"
