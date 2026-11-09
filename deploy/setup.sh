#!/usr/bin/env bash
# Crée deploy/.env à partir de .env.example : mots de passe PostgreSQL et JWT_SECRET générés.
# Propose d'utiliser Brevo pour les emails (sinon Mailpit).
#   ./setup.sh           crée .env (ne touche pas un .env existant)
#   ./setup.sh --force   recrée .env (puis : docker compose down -v, les bases gardent l'ancien mot de passe)
set -euo pipefail
cd "$(dirname "$0")"

command -v openssl >/dev/null || { echo "openssl introuvable" >&2; exit 1; }
[ -f .env.example ] || { echo ".env.example introuvable" >&2; exit 1; }

if [ -f .env ] && [ "${1:-}" != "--force" ]; then
    echo ".env existe déjà (./setup.sh --force pour le recréer)"
    exit 0
fi

set_var() { # clé valeur -> remplace la ligne CLE=... de .env
    K="$1" V="$2" awk 'BEGIN{k=ENVIRON["K"];v=ENVIRON["V"]}
        index($0,k"=")==1 && !d {print k"="v; d=1; next} {print}' .env > .env.tmp
    mv .env.tmp .env
}

umask 077
cp .env.example .env

for key in USER_DB_PASSWORD ALUMNI_STUDENT_COMPANY_DB_PASSWORD JOB_OFFER_DB_PASSWORD \
           ANALYTICS_MATCHING_DB_PASSWORD NOTIFICATION_MESSAGING_DB_PASSWORD; do
    set_var "$key" "$(openssl rand -base64 24 | tr -d '=+/\n')"
done
set_var JWT_SECRET "$(openssl rand -base64 64 | tr -d '\n')"

if [ -t 0 ]; then
    read -r -p "Identifiant SMTP Brevo (vide = Mailpit) : " login || true
    if [ -n "${login:-}" ]; then
        read -r -s -p "Clé SMTP Brevo : " key || true; echo
        read -r -p "Expéditeur validé dans Brevo : " from || true
        set_var MAIL_HOST smtp-relay.brevo.com
        set_var MAIL_PORT 587
        set_var MAIL_USERNAME "$login"
        set_var MAIL_PASSWORD "${key:-}"
        [ -n "${from:-}" ] && set_var MAIL_FROM "$from"
    fi
fi

chmod 600 .env
echo ".env prêt. Lancez : docker compose up -d --build"
