#!/bin/sh
# Premier démarrage de PostgreSQL : une base et un utilisateur par microservice.
# Chaque service ne voit que sa base (même isolation qu'avec une instance par service).
set -eu

create() { # create <utilisateur> <base> <mot de passe>
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v usr="$1" -v db="$2" -v pwd="$3" <<'SQL'
CREATE ROLE :"usr" LOGIN PASSWORD :'pwd';
CREATE DATABASE :"db" OWNER :"usr";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
SQL
}

create user_svc                   user_db                   "$USER_DB_PASSWORD"
create alumni_student_company_svc alumni_student_company_db "$ALUMNI_STUDENT_COMPANY_DB_PASSWORD"
create job_offer_svc              job_offer_db              "$JOB_OFFER_DB_PASSWORD"
create analytics_matching_svc     analytics_matching_db     "$ANALYTICS_MATCHING_DB_PASSWORD"
create notification_messaging_svc notification_messaging_db "$NOTIFICATION_MESSAGING_DB_PASSWORD"
