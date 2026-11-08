#!/usr/bin/env bash
# Test de bout en bout : changement de statut de candidature -> notification de l'étudiant.
#
# Lance les VRAIS job-offer-service et notification-messaging-service (jars), chacun avec sa base
# PostgreSQL (conteneurs Docker éphémères), puis joue le flux par HTTP :
#   1. un alumni publie une offre                        POST  job-offer      /offers
#   2. un étudiant postule                               POST  job-offer      /offers/{id}/applications
#   3. l'alumni présélectionne la candidature            PATCH job-offer      /applications/{id}/status
#      -> job-offer appelle notification-messaging avec un JETON DE SERVICE (ADR-0010)
#   4. l'étudiant voit la notification                   GET   notification   /notifications
#   5. panne : notification-messaging est arrêté, le changement de statut réussit quand même
#      (timeout + retries journalisés, ADR-0006)
#
# Pré-requis : Docker démarré, JDK 21 (JAVA_HOME), curl, openssl.
# Usage : ./scripts/e2e/application-status-notification.sh   (depuis la racine du dépôt)
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT="$(pwd)"
WORK="$(mktemp -d)"
PIDS=()
CONTAINERS=()

JOB_PORT=18083
NOTIF_PORT=18085
JOB_DB_PORT=55434
NOTIF_DB_PORT=55435

say()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  OK\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m  ECHEC\033[0m %s\n' "$*" >&2; exit 1; }

cleanup() {
    for p in "${PIDS[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null || true; done
    for c in "${CONTAINERS[@]:-}"; do [ -n "$c" ] && docker rm -f "$c" >/dev/null 2>&1 || true; done
    echo; echo "Journaux conservés dans : $WORK"
}
trap cleanup EXIT

for cmd in docker curl openssl java; do command -v "$cmd" >/dev/null || die "commande requise : $cmd"; done
docker info >/dev/null 2>&1 || die "Docker n'est pas démarré"

# ---------- Jetons JWT HS256 (même format que user-service / ADR-0005) ----------
SECRET="$(openssl rand -base64 48 | tr -d '\n')"
HEXKEY="$(printf '%s' "$SECRET" | openssl base64 -d -A | od -An -tx1 | tr -d ' \n')"
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
jwt() { # sub role
    local now header payload sig
    now="$(date +%s)"
    header="$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)"
    payload="$(printf '{"iss":"alumni-uasz-user","sub":"%s","role":"%s","iat":%s,"exp":%s}' "$1" "$2" "$now" "$((now + 900))" | b64url)"
    sig="$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -mac HMAC -macopt "hexkey:$HEXKEY" -binary | b64url)"
    printf '%s.%s.%s' "$header" "$payload" "$sig"
}
uuid() { # UUID v4 aléatoire
    local h; h="$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"
    printf '%s-%s-4%s-a%s-%s' "${h:0:8}" "${h:8:4}" "${h:13:3}" "${h:17:3}" "${h:20:12}"
}
json_field() { sed -nE "s/.*\"$1\":\"?([^\",}]*)\"?.*/\1/p" | head -1; }

RECRUITER="$(uuid)"
STUDENT="$(uuid)"
RECRUITER_TOKEN="$(jwt "$RECRUITER" ALUMNI)"
STUDENT_TOKEN="$(jwt "$STUDENT" ETUDIANT)"

# ---------- Build ----------
say "Build des deux services"
(cd job-offer-service && ./mvnw -B -q -ntp -DskipTests package)
(cd notification-messaging-service && ./mvnw -B -q -ntp -DskipTests package)
ok "jars construits"

# ---------- Bases PostgreSQL ----------
start_db() { # nom port db
    local name="e2e-$1-$$"
    docker run -d --rm --name "$name" -e POSTGRES_DB="$3" -e POSTGRES_USER=e2e -e POSTGRES_PASSWORD=e2e \
        -p "127.0.0.1:$2:5432" postgres:16-alpine >/dev/null
    CONTAINERS+=("$name")
    for _ in $(seq 1 60); do
        docker exec "$name" pg_isready -U e2e -d "$3" >/dev/null 2>&1 && return 0
        sleep 1
    done
    die "PostgreSQL $1 ne démarre pas"
}
say "Démarrage des bases PostgreSQL"
start_db job "$JOB_DB_PORT" job_offer_db
start_db notif "$NOTIF_DB_PORT" notification_db
ok "2 bases prêtes"

# ---------- Services ----------
start_service() { # dossier port db_port db_name [env supplémentaires...]
    local dir="$1" port="$2" dbport="$3" db="$4"; shift 4
    env SERVER_PORT="$port" FORWARD_HEADERS_STRATEGY=none API_PREFIX=/v1 CORS_ALLOWED_ORIGINS=http://localhost:4200 \
        JWT_SECRET="$SECRET" JWT_ISSUER=alumni-uasz-user \
        SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$dbport/$db" \
        SPRING_DATASOURCE_USERNAME=e2e SPRING_DATASOURCE_PASSWORD=e2e "$@" \
        java -jar "$(ls "$ROOT/$dir"/target/*.jar | grep -v plain | head -1)" > "$WORK/$dir.log" 2>&1 &
    PIDS+=("$!")
    LAST_PID="$!"
    for _ in $(seq 1 90); do
        curl -fs "http://localhost:$port/actuator/health/readiness" >/dev/null 2>&1 && return 0
        kill -0 "$LAST_PID" 2>/dev/null || die "$dir s'est arrêté, voir $WORK/$dir.log"
        sleep 1
    done
    die "$dir ne répond pas, voir $WORK/$dir.log"
}
say "Démarrage de notification-messaging-service et job-offer-service"
start_service notification-messaging-service "$NOTIF_PORT" "$NOTIF_DB_PORT" notification_db
NOTIF_PID="$LAST_PID"
start_service job-offer-service "$JOB_PORT" "$JOB_DB_PORT" job_offer_db \
    NOTIFICATION_SERVICE_URL="http://localhost:$NOTIF_PORT/v1"
ok "services prêts"

JOB="http://localhost:$JOB_PORT/v1"
NOTIF="http://localhost:$NOTIF_PORT/v1"
call() { # méthode url jeton [corps] -> écrit le corps, échoue si code inattendu
    local method="$1" url="$2" token="$3" body="${4:-}" expected="${5:-2}"
    local out code
    out="$(curl -s -w '\n%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' ${body:+-d "$body"})"
    code="$(printf '%s' "$out" | tail -1)"
    [[ "$code" == "$expected"* ]] || die "$method $url -> HTTP $code : $(printf '%s' "$out" | sed '$d')"
    printf '%s' "$out" | sed '$d'
}

# ---------- Flux ----------
say "1. L'alumni publie une offre"
OFFER_ID="$(call POST "$JOB/offers" "$RECRUITER_TOKEN" \
    "{\"title\":\"Stage développeur Java\",\"type\":\"INTERNSHIP\",\"description\":\"Stage de 6 mois\",\"companyId\":\"$(uuid)\",\"requiredSkills\":[\"Java\",\"Spring\"]}" \
    | json_field id)"
ok "offre $OFFER_ID"

say "2. L'étudiant postule"
APP_ID="$(call POST "$JOB/offers/$OFFER_ID/applications" "$STUDENT_TOKEN" \
    "{\"studentId\":\"$(uuid)\",\"coverLetter\":\"Très motivé\"}" | json_field id)"
ok "candidature $APP_ID"

say "3. L'alumni présélectionne la candidature"
call PATCH "$JOB/applications/$APP_ID/status" "$RECRUITER_TOKEN" '{"status":"SHORTLISTED"}' >/dev/null
ok "statut SHORTLISTED enregistré"

say "4. L'étudiant consulte ses notifications"
for _ in $(seq 1 20); do
    LIST="$(call GET "$NOTIF/notifications" "$STUDENT_TOKEN")"
    [ "$(printf '%s' "$LIST" | json_field totalElements)" = "1" ] && break
    sleep 0.5
done
[ "$(printf '%s' "$LIST" | json_field totalElements)" = "1" ] || die "aucune notification reçue : $LIST"
printf '%s' "$LIST" | grep -q '"type":"APPLICATION_STATUS_CHANGED"' || die "type inattendu : $LIST"
printf '%s' "$LIST" | grep -q "\"referenceId\":\"$APP_ID\"" || die "referenceId inattendu : $LIST"
ok "notification reçue : $(printf '%s' "$LIST" | json_field content)"
grep -q "par job-offer-service" "$WORK/notification-messaging-service.log" \
    && ok "émise par job-offer-service avec un jeton de service" \
    || die "émetteur inattendu, voir $WORK/notification-messaging-service.log"

say "5. Panne : notification-messaging-service est arrêté"
kill "$NOTIF_PID"; wait "$NOTIF_PID" 2>/dev/null || true
START="$(date +%s%N)"
call PATCH "$JOB/applications/$APP_ID/status" "$RECRUITER_TOKEN" '{"status":"ACCEPTED"}' >/dev/null
ELAPSED_MS="$(( ($(date +%s%N) - START) / 1000000 ))"
ok "statut ACCEPTED enregistré malgré la panne (réponse en ${ELAPSED_MS} ms)"
for _ in $(seq 1 30); do
    grep -q "Notification non délivrée" "$WORK/job-offer-service.log" && break
    sleep 1
done
grep -q "Notification non délivrée" "$WORK/job-offer-service.log" \
    && ok "retries épuisés puis abandon journalisé : $(grep 'Notification non délivrée' "$WORK/job-offer-service.log" | sed -E 's/.*(Notification non délivrée.*)/\1/' | cut -c1-120)" \
    || die "abandon non journalisé, voir $WORK/job-offer-service.log"

say "Flux de bout en bout validé"
