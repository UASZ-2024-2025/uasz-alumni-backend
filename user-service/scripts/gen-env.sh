#!/usr/bin/env bash
# Crée / complète / régénère .env à partir de .env.example. Aucun .env.bak n'est créé.
#   ./scripts/gen-env.sh             crée .env (POSTGRES_PASSWORD généré ; JWT_SECRET généré ou copié)
#   ./scripts/gen-env.sh --force     régénère POSTGRES_PASSWORD (+ JWT_SECRET) ; conserve le reste
#                                    ATTENTION : faites ensuite `make reset-db`
#   ./scripts/gen-env.sh --complete  ajoute à .env les clés manquantes de .env.example
#   ./scripts/gen-env.sh --jwt-only  recopie JWT_SECRET depuis le service émetteur (mode copy)
. "$(dirname "$0")/_common.sh"
require openssl
require awk
umask 077

MODE="${1:-}"
case "$MODE" in ''|--force|--complete|--jwt-only) ;; *) fail "option inconnue : $MODE" ;; esac
[ -f .env.example ] || fail ".env.example introuvable"

SECRET_KEYS=(POSTGRES_PASSWORD JWT_SECRET BOOTSTRAP_ADMIN_PASSWORD)

get_var() { # fichier clé -> valeur brute (espaces conservés)
    local line
    line="$(grep -m1 "^$2=" "$1" 2>/dev/null || true)"
    printf '%s' "${line#*=}"
}

set_var() { # clé valeur -> remplace la ligne CLE=... de .env (sans souci d'échappement)
    K="$1" V="$2" awk 'BEGIN{k=ENVIRON["K"];v=ENVIRON["V"]}
        index($0,k"=")==1 && !d {print k"="v; d=1; next} {print}' .env > .env.tmp
    mv .env.tmp .env
}

# Mot de passe de l'admin initial : conforme à la politique (lettre + chiffre, 8 à 72 octets)
new_admin_password() { printf '%sAa1' "$(openssl rand -base64 18 | tr -d '=+/\n')"; }

example_keys() {
    local line
    while IFS= read -r line || [ -n "$line" ]; do
        if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)= ]]; then printf '%s\n' "${BASH_REMATCH[1]}"; fi
    done < .env.example
}

# Lit JWT_SECRET du service émetteur ; échoue avec un message clair si impossible.
issuer_jwt_secret() {
    [ -f "$ISSUER_ENV" ] || fail "$ISSUER_ENV introuvable : lancez d'abord 'make setup' dans user-service"
    local v
    v="$(get_var "$ISSUER_ENV" JWT_SECRET)"
    if [[ "$v" == \"*\" || "$v" == \'*\' ]]; then v="${v:1:${#v}-2}"; fi
    [ -n "$v" ] || fail "JWT_SECRET vide dans $ISSUER_ENV"
    case "$v" in CHANGE_ME*) fail "JWT_SECRET vaut encore CHANGE_ME dans $ISSUER_ENV" ;; esac
    printf '%s' "$v"
}

# Valeur de JWT_SECRET à utiliser : générée (émetteur) ou copiée (autres services)
new_jwt() {
    if [ "$JWT_MODE" = "generate" ]; then openssl rand -base64 64 | tr -d '\n'
    else issuer_jwt_secret; fi
}

jwt_is_placeholder() {
    local v; v="$(get_var .env JWT_SECRET)"
    [ -z "$v" ] || [[ "$v" == CHANGE_ME* ]]
}

# ── --jwt-only ──
if [ "$MODE" = "--jwt-only" ]; then
    [ "$JWT_MODE" = "copy" ] || fail "Ce service génère lui-même JWT_SECRET : utilisez 'make env' (puis 'make jwt-sync' dans les autres services)"
    [ -f .env ] || fail ".env introuvable : lancez 'make setup'"
    grep -q '^JWT_SECRET=' .env || printf 'JWT_SECRET=\n' >> .env
    set_var JWT_SECRET "$(new_jwt)"
    chmod 600 .env
    info "JWT_SECRET copié depuis $ISSUER_ENV"
    exit 0
fi

# ── --complete : ajoute seulement les clés manquantes ──
if [ "$MODE" = "--complete" ]; then
    [ -f .env ] || fail ".env introuvable : lancez 'make setup'"
    added=()
    for key in $(example_keys); do
        if ! grep -q "^${key}=" .env; then
            if [ "${#added[@]}" -eq 0 ]; then
                { [ -z "$(tail -c1 .env)" ] || echo; printf '\n# --- Ajouté par --complete ---\n'; } >> .env
            fi
            printf '%s=%s\n' "$key" "$(get_var .env.example "$key")" >> .env
            added+=("$key")
        fi
    done
    chmod 600 .env
    if [ "${#added[@]}" -eq 0 ]; then info ".env déjà complet"; else info "Clés ajoutées à .env : ${added[*]}"; fi
    if jwt_is_placeholder; then
        if [ "$JWT_MODE" = "generate" ]; then set_var JWT_SECRET "$(new_jwt)"; info "JWT_SECRET généré"
        elif [ -f "$ISSUER_ENV" ]; then set_var JWT_SECRET "$(new_jwt)"; info "JWT_SECRET copié depuis $ISSUER_ENV"
        else warn "JWT_SECRET à renseigner ($ISSUER_ENV introuvable) : 'make jwt-sync'"; fi
    fi
    if [ "$(get_var .env POSTGRES_PASSWORD)" = "CHANGE_ME" ]; then
        warn "POSTGRES_PASSWORD vaut CHANGE_ME : lancez 'make env' puis 'make reset-db'"
    fi
    if [[ "$(get_var .env BOOTSTRAP_ADMIN_PASSWORD)" == CHANGE_ME* ]]; then
        set_var BOOTSTRAP_ADMIN_PASSWORD "$(new_admin_password)"; info "BOOTSTRAP_ADMIN_PASSWORD généré"
    fi
    exit 0
fi

# ── création / régénération ──
OLD=""
if [ -f .env ]; then
    if [ "$MODE" != "--force" ]; then
        info ".env existe déjà (--force : régénère les secrets ; --complete : clés manquantes ; --jwt-only : secret JWT)"
        exit 0
    fi
    OLD="$(mktemp)"
    trap 'rm -f "$OLD"' EXIT
    cp .env "$OLD"
fi

# Vérifie tout de suite que le secret de l'émetteur est disponible (avant d'écraser .env)
JWT_VALUE="$(new_jwt)"

cp .env.example .env

if [ -n "$OLD" ]; then
    # On reprend toutes les valeurs de l'ancien .env sauf les secrets (régénérés plus bas)
    for key in $(example_keys); do
        skipkey=0
        for s in "${SECRET_KEYS[@]}"; do [ "$key" = "$s" ] && skipkey=1; done
        [ "$skipkey" = 1 ] && continue
        if grep -q "^${key}=" "$OLD"; then set_var "$key" "$(get_var "$OLD" "$key")"; fi
    done
elif [ -t 0 ]; then
    def="$(get_var .env POSTGRES_USER)"
    read -r -p "Utilisateur PostgreSQL [${def}] : " ans || true
    [ -n "${ans:-}" ] && set_var POSTGRES_USER "$ans"
fi

# Mot de passe sans caractère | & = + /
set_var POSTGRES_PASSWORD "$(openssl rand -base64 24 | tr -d '=+/\n')"
set_var JWT_SECRET "$JWT_VALUE"
set_var BOOTSTRAP_ADMIN_PASSWORD "$(new_admin_password)"
info "Mot de passe de l'administrateur initial : voir BOOTSTRAP_ADMIN_PASSWORD dans .env"
chmod 600 .env

if [ "$JWT_MODE" = "generate" ]; then
    info ".env prêt (permissions 600) : POSTGRES_PASSWORD et JWT_SECRET générés"
    [ -z "$OLD" ] || warn "JWT_SECRET changé : lancez 'make jwt-sync' dans les autres services."
else
    info ".env prêt (permissions 600) : POSTGRES_PASSWORD généré, JWT_SECRET copié depuis user-service"
fi
if [ -n "$OLD" ]; then
    warn "Mot de passe changé : lancez 'make reset-db' (le volume Postgres garde l'ancien mot de passe)."
fi
