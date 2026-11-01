#!/usr/bin/env bash
# Fonctions communes : se place à la racine du projet, charge .env, helpers d'affichage.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# generate : ce service ÉMET les JWT (il génère JWT_SECRET)
# copy     : ce service les VALIDE (il copie JWT_SECRET depuis le service émetteur)
JWT_MODE="generate"
ISSUER_ENV="../user-service/.env"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31mErreur :\033[0m %s\n' "$*" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "'$1' est requis mais introuvable"; }

# Valeurs qui ne doivent jamais être vides ni égales au CHANGE_ME de .env.example
REQUIRED_NONEMPTY=(POSTGRES_USER POSTGRES_PASSWORD JWT_SECRET)

# Toutes les clés de .env.example doivent exister dans l'environnement chargé.
check_env() {
    local line key
    local missing=()
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in ''|'#'*) continue ;; esac
        key="${line%%=*}"
        [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
        [ -n "${!key+x}" ] || missing+=("$key")
    done < .env.example
    if [ "${#missing[@]}" -gt 0 ]; then
        fail "Variables absentes de .env : ${missing[*]} (lancez ./scripts/gen-env.sh --complete)"
    fi
    for key in "${REQUIRED_NONEMPTY[@]}"; do
        [ -n "${!key}" ] || fail "$key est vide dans .env (renseignez-le ou lancez 'make env')"
        case "${!key}" in
            CHANGE_ME*) fail "$key vaut encore CHANGE_ME dans .env (lancez 'make env')" ;;
        esac
    done
}

# Avertit (sans bloquer) si JWT_SECRET diffère de celui du service émetteur : jetons refusés (401).
check_jwt_shared() {
    [ "$JWT_MODE" = "copy" ] || return 0
    [ -f "$ISSUER_ENV" ] || return 0
    local line issuer_secret
    line="$(grep -m1 '^JWT_SECRET=' "$ISSUER_ENV" 2>/dev/null || true)"
    issuer_secret="${line#*=}"
    if [[ "$issuer_secret" == \"*\" || "$issuer_secret" == \'*\' ]]; then issuer_secret="${issuer_secret:1:${#issuer_secret}-2}"; fi
    if [ -n "$issuer_secret" ] && [ "$issuer_secret" != "${JWT_SECRET:-}" ]; then
        warn "JWT_SECRET différent de celui de user-service : les jetons seront refusés (401). Lancez 'make jwt-sync'."
    fi
}

# Charge .env dans l'environnement du script (et donc de mvnw / docker compose).
load_env() {
    [ -f .env ] || fail ".env introuvable : lancez 'make setup'"
    [ -f .env.example ] || fail ".env.example introuvable"
    # Lecture littérale ligne par ligne (pas de "source") : supporte les valeurs avec espaces.
    local line key val
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in ''|'#'*) continue ;; esac
        key="${line%%=*}"; val="${line#*=}"
        [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
        if [[ "$val" == \"*\" || "$val" == \'*\' ]]; then val="${val:1:${#val}-2}"; fi
        export "$key=$val"
    done < .env
    check_env
    check_jwt_shared
}
