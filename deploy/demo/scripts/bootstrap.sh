#!/usr/bin/env bash
# Prépare une machine Ubuntu (22.04/24.04, amd64 ou arm64) pour la démo. Idempotent.
# Rien n'est propre à AWS, sauf les options --ssm-prefix / --backup-bucket.
#
#   bootstrap.sh --role vm-a|vm-b|all --domain <nom> [options]
#
#   --role           vm-a, vm-b, ou all (tout sur un seul serveur)
#   --domain         nom public de la démo (ex. alumni-demo.203.0.113.10.sslip.io)
#   --db-host        (vm-b) IP privée de vm-a
#   --vm-b-host      (vm-a) IP privée de vm-b
#   --vm-b-host-param (vm-a, AWS) paramètre SSM qui contient l'IP privée de vm-b
#   --ssm-prefix     (AWS) secrets partagés entre les machines dans SSM Parameter Store
#   --backup-bucket  (AWS) bucket S3 qui reçoit une copie des sauvegardes
#   --repo / --ref   dépôt backend à cloner (défaut : dépôt public, branche develop)
#
# Exemple, futur serveur de l'université :
#   sudo ./bootstrap.sh --role all --domain alumni.uasz.sn
set -euo pipefail

ROLE=""; DOMAIN=""; DB_HOST=""; VM_B_HOST=""; VM_B_PARAM=""; SSM_PREFIX=""; BUCKET=""
REPO="https://github.com/UASZ-2024-2025/uasz-alumni-backend.git"; REF="develop"
while [ $# -gt 0 ]; do
  case "$1" in
    --role) ROLE="$2" ;; --domain) DOMAIN="$2" ;; --db-host) DB_HOST="$2" ;;
    --vm-b-host) VM_B_HOST="$2" ;; --vm-b-host-param) VM_B_PARAM="$2" ;;
    --ssm-prefix) SSM_PREFIX="$2" ;; --backup-bucket) BUCKET="$2" ;;
    --repo) REPO="$2" ;; --ref) REF="$2" ;;
    *) echo "option inconnue : $1" >&2; exit 2 ;;
  esac
  shift 2
done
case "$ROLE" in vm-a|vm-b|all) ;; *) echo "--role vm-a|vm-b|all obligatoire" >&2; exit 2 ;; esac
[ "$ROLE" = vm-b ] || [ -n "$DOMAIN" ] || { echo "--domain obligatoire" >&2; exit 2; }
[ "$ROLE" != vm-b ] || [ -n "$DB_HOST" ] || { echo "--db-host obligatoire pour vm-b" >&2; exit 2; }
[ "$(id -u)" -eq 0 ] || { echo "à lancer en root (sudo)" >&2; exit 1; }

ROOT=/opt/alumni
log() { printf '[bootstrap] %s\n' "$*"; }
export DEBIAN_FRONTEND=noninteractive
# cloud-init n'a pas /snap/bin dans son PATH (AWS CLI installée par snap)
export PATH="$PATH:/snap/bin"

# ---------------------------------------------------------------- paquets + Docker
if ! command -v docker >/dev/null; then
  log "installation de Docker"
  apt-get update -q
  apt-get install -yq ca-certificates curl git jq unattended-upgrades
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -yq docker-ce docker-ce-cli containerd.io docker-compose-plugin
fi
# Journaux Docker limités (disque de 20 Go)
if [ ! -f /etc/docker/daemon.json ]; then
  echo '{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"3"}}' > /etc/docker/daemon.json
  systemctl restart docker
fi
systemctl enable --now docker >/dev/null

# Swap de secours (1 Go) : filet de sécurité, la mémoire est dimensionnée pour s'en passer
if ! swapon --show | grep -q .; then
  log "création d'un swap de 1 Go"
  fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap -q /swapfile && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -q vm.swappiness=10 && echo 'vm.swappiness=10' > /etc/sysctl.d/99-alumni.conf
fi

if [ -n "$SSM_PREFIX$BUCKET$VM_B_PARAM" ] && ! command -v aws >/dev/null; then
  log "installation de l'AWS CLI"
  snap install aws-cli --classic >/dev/null
fi
# Région lue dans les métadonnées de l'instance (IMDSv2)
if command -v aws >/dev/null && [ -z "${AWS_DEFAULT_REGION:-}" ]; then
  imds_token=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token \
    -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2>/dev/null || true)
  AWS_DEFAULT_REGION=$(curl -fsS -H "X-aws-ec2-metadata-token: $imds_token" \
    http://169.254.169.254/latest/meta-data/placement/region 2>/dev/null || true)
  export AWS_DEFAULT_REGION
fi

# ---------------------------------------------------------------- dépôt
mkdir -p "$ROOT/backups"
if [ ! -d "$ROOT/app/.git" ]; then
  log "clonage de $REPO ($REF)"
  git clone -q --branch "$REF" "$REPO" "$ROOT/app"
fi

# ---------------------------------------------------------------- secrets
SECRET_KEYS="JWT_SECRET PG_SUPERUSER_PASSWORD USER_DB_PASSWORD ALUMNI_STUDENT_COMPANY_DB_PASSWORD JOB_OFFER_DB_PASSWORD ANALYTICS_MATCHING_DB_PASSWORD NOTIFICATION_MESSAGING_DB_PASSWORD BOOTSTRAP_ADMIN_PASSWORD MAILPIT_UI_AUTH"
gen_secrets() {
  echo "JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')"
  for k in PG_SUPERUSER_PASSWORD USER_DB_PASSWORD ALUMNI_STUDENT_COMPANY_DB_PASSWORD JOB_OFFER_DB_PASSWORD \
           ANALYTICS_MATCHING_DB_PASSWORD NOTIFICATION_MESSAGING_DB_PASSWORD; do
    echo "$k=$(openssl rand -hex 24)"
  done
  echo "BOOTSTRAP_ADMIN_PASSWORD=Demo-$(openssl rand -hex 6)-$(openssl rand -hex 6 | tr a-f A-F)!"
  echo "MAILPIT_UI_AUTH=demo:$(openssl rand -hex 12)"
}
secrets_file=$(mktemp)
trap 'rm -f "$secrets_file"' EXIT
if [ -f "$ROOT/demo.env" ]; then
  grep -E "^($(echo $SECRET_KEYS | tr ' ' '|'))=" "$ROOT/demo.env" > "$secrets_file"
elif [ -n "$SSM_PREFIX" ]; then
  # Secrets communs aux deux machines : vm-a (ou all) les crée, vm-b les attend.
  param="$SSM_PREFIX/secrets"
  if [ "$ROLE" != vm-b ] && ! aws ssm get-parameter --name "$param" >/dev/null 2>&1; then
    log "création des secrets dans SSM ($param)"
    gen_secrets > "$secrets_file"
    aws ssm put-parameter --name "$param" --type SecureString --value "file://$secrets_file" >/dev/null
  fi
  for _ in $(seq 1 60); do
    aws ssm get-parameter --name "$param" --with-decryption --query Parameter.Value --output text \
      > "$secrets_file" 2>/dev/null && break
    log "attente des secrets ($param)"; sleep 10
  done
  [ -s "$secrets_file" ] || { echo "secrets introuvables dans $param" >&2; exit 1; }
else
  gen_secrets > "$secrets_file"
fi

# ---------------------------------------------------------------- réseau
PRIVATE_IP=$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<NF;i++) if($i=="src") print $(i+1)}')
case "$ROLE" in
  all)  PROFILES="vm-a,vm-b"; BIND=127.0.0.1;     DB=db;          B_HOST="" ;;
  vm-a) PROFILES="vm-a";      BIND="$PRIVATE_IP"; DB=db;          B_HOST="$VM_B_HOST" ;;
  vm-b) PROFILES="vm-b";      BIND="$PRIVATE_IP"; DB="$DB_HOST";  B_HOST="" ;;
esac
if [ "$ROLE" = vm-a ] && [ -z "$B_HOST" ] && [ -n "$VM_B_PARAM" ]; then
  for _ in $(seq 1 60); do
    B_HOST=$(aws ssm get-parameter --name "$VM_B_PARAM" --query Parameter.Value --output text 2>/dev/null) && break
    log "attente de l'IP de vm-b ($VM_B_PARAM)"; sleep 10
  done
fi
[ "$ROLE" != vm-a ] || [ -n "$B_HOST" ] || { echo "IP de vm-b inconnue (--vm-b-host)" >&2; exit 1; }

# ---------------------------------------------------------------- demo.env
log "écriture de $ROOT/demo.env (profils : $PROFILES)"
{
  echo "# Généré par bootstrap.sh le $(date -u +%F) - ne pas versionner"
  echo "COMPOSE_PROFILES=$PROFILES"
  echo "DEMO_DOMAIN=${DOMAIN:-unused.invalid}"
  echo "PRIVATE_BIND=$BIND"
  echo "DB_HOST=$DB"
  if [ -n "$B_HOST" ]; then
    echo "UPSTREAM_JOB_OFFER=$B_HOST:8083"
    echo "UPSTREAM_ANALYTICS=$B_HOST:8084"
    echo "UPSTREAM_NOTIFICATION=$B_HOST:8085"
    echo "NOTIFICATION_SERVICE_URL=http://$B_HOST:8085/notification-messaging-service/v1"
  fi
  # vm-b : fiches entreprises lues sur vm-a par l'IP privée (le nom du conteneur n'existe que sur vm-a)
  [ "$ROLE" != vm-b ] || echo "STUDENT_COMPANY_SERVICE_URL=http://$DB_HOST:8082/alumni-student-company-service/v1"
  [ -z "$BUCKET" ] || echo "BACKUP_BUCKET=$BUCKET"
  [ -z "${AWS_DEFAULT_REGION:-}" ] || echo "AWS_DEFAULT_REGION=$AWS_DEFAULT_REGION"
  # SMTP réel facultatif, relu par deploy.sh sur les deux machines : user-service (vm-a) et les e-mails de
  # notification-messaging-service (vm-b, ALUM-135 ; Mailpit, sur vm-a, n'est pas joignable depuis vm-b)
  if [ -n "$SSM_PREFIX" ]; then echo "MAIL_SSM_PARAM=$SSM_PREFIX/mail"; fi
  cat "$secrets_file"
} > "$ROOT/demo.env.new"
# Conserver les réglages ajoutés à la main (ex. MAIL_HOST pour Brevo)
if [ -f "$ROOT/demo.env" ]; then
  grep -E '^(MAIL_(HOST|PORT|USERNAME|PASSWORD|FROM|STARTTLS)=|JVM_HEAP|FRONTEND_IMAGE|IMAGE_PREFIX|BOOTSTRAP_ADMIN_EMAIL)' "$ROOT/demo.env" >> "$ROOT/demo.env.new" || true
fi
install -m 600 "$ROOT/demo.env.new" "$ROOT/demo.env" && rm -f "$ROOT/demo.env.new"
touch "$ROOT/tags.env"

# ---------------------------------------------------------------- sauvegarde quotidienne
if [ "$ROLE" != vm-b ]; then
  echo "30 2 * * * root $ROOT/app/deploy/demo/scripts/backup.sh >> /var/log/alumni-backup.log 2>&1" \
    > /etc/cron.d/alumni-backup
fi

# ---------------------------------------------------------------- premier démarrage
log "premier déploiement"
if ! "$ROOT/app/deploy/demo/scripts/deploy.sh"; then
  log "démarrage incomplet (images pas encore publiées ?) : le prochain déploiement GitHub s'en chargera"
fi
log "terminé"
