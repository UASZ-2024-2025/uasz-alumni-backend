#!/usr/bin/env python3
"""
Applique au monorepo uasz-alumni-backend les changements AL-19 / AL-20 du user-service :
contrat OpenAPI, SPI, pom, config, migration V3, code Java, scripts, K8s, docs.

Usage (depuis la racine du monorepo) :
    python3 apply_auth_changes.py --dry-run      # simule, n'écrit rien
    python3 apply_auth_changes.py                # applique + régénère le SPI 1.1.0
    python3 apply_auth_changes.py --skip-spi     # applique sans lancer Maven (SPI à faire à la main)

Garanties :
  * tout est calculé en mémoire d'abord : si UNE ancre est introuvable, RIEN n'est écrit ;
  * idempotent : relancé, il détecte ce qui est déjà appliqué ;
  * LF partout, pas de fichier .bak (utilisez git diff pour relire).
"""
import argparse
import subprocess
import sys
from pathlib import Path

BASE = "user-service/src/main/java/com/uasz/alumni/user"
RES = "user-service/src/main/resources"

# ----------------------------------------------------------------------------
# Moteur d'édition en mémoire
# ----------------------------------------------------------------------------
FILES = {}      # chemin relatif -> contenu final
ORIGINAL = {}   # chemin relatif -> contenu d'origine (None si nouveau fichier)
LOG = []
ERRORS = []
ROOT = Path(".")


def load(rel):
    if rel not in FILES:
        p = ROOT / rel
        if not p.exists():
            ERRORS.append(f"fichier introuvable : {rel}")
            return None
        text = p.read_text(encoding="utf-8").replace("\r\n", "\n")
        FILES[rel] = text
        ORIGINAL[rel] = text
    return FILES[rel]


def sub(rel, old, new, label, skip_if=None, count=1):
    """Remplace `old` par `new` dans rel. `skip_if` : marqueur indiquant que c'est déjà fait."""
    text = load(rel)
    if text is None:
        return
    if skip_if and skip_if in text:
        LOG.append(f"  = déjà fait  {rel} : {label}")
        return
    n = text.count(old)
    if n < 1:
        ERRORS.append(f"ancre introuvable dans {rel} : {label}")
        return
    if count and n != count:
        ERRORS.append(f"{rel} : {label} : {n} occurrence(s) trouvée(s), {count} attendue(s)")
        return
    FILES[rel] = text.replace(old, new)
    LOG.append(f"  ~ modifié    {rel} : {label}")


def append(rel, block, label, skip_if):
    text = load(rel)
    if text is None:
        return
    if skip_if in text:
        LOG.append(f"  = déjà fait  {rel} : {label}")
        return
    if not text.endswith("\n"):
        text += "\n"
    FILES[rel] = text + block
    LOG.append(f"  ~ complété   {rel} : {label}")


def put(rel, content):
    """Crée ou remplace un fichier entier."""
    content = content.strip("\n") + "\n"
    p = ROOT / rel
    old = p.read_text(encoding="utf-8").replace("\r\n", "\n") if p.exists() else None
    if rel not in ORIGINAL:
        ORIGINAL[rel] = old
    if old == content:
        LOG.append(f"  = identique  {rel}")
        FILES[rel] = content
        return
    FILES[rel] = content
    LOG.append(f"  {'+ créé       ' if old is None else '! remplacé   '}{rel}")


# ----------------------------------------------------------------------------
# 1. Contrat OpenAPI
# ----------------------------------------------------------------------------
def contract():
    f = "contracts/user-service.yaml"

    sub(f, r'''    RoleName:
      type: string
      enum:
        - ALUMNI
        - ETUDIANT
        - ENSEIGNANT
        - ADMINISTRATEUR
''', r'''    RoleName:
      type: string
      enum:
        - ALUMNI
        - ETUDIANT
        - ENSEIGNANT
        - ENTREPRISE
        - ADMINISTRATEUR
    Gender:
      type: string
      enum:
        - MALE
        - FEMALE
''', "RoleName + ENTREPRISE, schéma Gender", skip_if="- ENTREPRISE")

    sub(f, r'''        gender:
          type: string
          enum:
            - MALE
            - FEMALE
''', r'''        gender:
          $ref: "#/components/schemas/Gender"
''', "Gender en $ref (User, UserUpdateRequest)",
        skip_if='$ref: "#/components/schemas/Gender"', count=2)

    sub(f, r'''          description: Compte non activé ou suspendu
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
''', r'''          description: Compte non activé ou suspendu
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
        "423":
          description: Compte temporairement verrouillé après trop d'échecs de connexion
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
''', "423 sur /auth/login", skip_if='        "423":')

    sub(f, "  /users:\n    get:\n", r'''  /auth/change-password:
    post:
      tags:
        - Auth
      summary: Changer son mot de passe (utilisateur connecté)
      description: |
        Révoque tous les refresh tokens de l'utilisateur : il doit se reconnecter.
        Politique : 8 à 72 octets, au moins une lettre et un chiffre.
      operationId: changePassword
      requestBody:
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/ChangePasswordRequest"
        required: true
      responses:
        "200":
          description: Mot de passe modifié
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/MessageResponse"
        "400":
          description: Mot de passe actuel incorrect ou nouveau mot de passe identique
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
        "401":
          $ref: "#/components/responses/Unauthorized"
        "422":
          description: Nouveau mot de passe non conforme à la politique de sécurité
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
      security:
        - bearerAuth: []
  /users:
    get:
''', "chemin /auth/change-password", skip_if="/auth/change-password:")

    sub(f, "  /users/{id}:\n    get:\n", r'''    post:
      tags:
        - Users
      summary: Créer un compte utilisateur (ADMINISTRATEUR)
      description: |
        Le compte est créé au statut EN_ATTENTE sans mot de passe utilisable. Un email invite
        l'utilisateur à définir son mot de passe (lien traité par POST /auth/reset-password,
        qui active le compte).
      operationId: createUser
      requestBody:
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/UserCreateRequest"
        required: true
      responses:
        "201":
          description: Compte créé
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/User"
        "400":
          $ref: "#/components/responses/BadRequest"
        "401":
          $ref: "#/components/responses/Unauthorized"
        "403":
          $ref: "#/components/responses/Forbidden"
        "409":
          $ref: "#/components/responses/Conflict"
      security:
        - bearerAuth: []
  /users/{id}:
    get:
''', "POST /users", skip_if="operationId: createUser")

    # GET /users/{id}
    sub(f, "      summary: Récupérer un utilisateur par son identifiant\n      operationId: getUserById\n",
        "      summary: Récupérer un utilisateur par son identifiant\n"
        "      description: Réservé à l'utilisateur lui-même ou à un ADMINISTRATEUR.\n"
        "      operationId: getUserById\n",
        "description GET /users/{id}", skip_if="Réservé à l'utilisateur lui-même")
    sub(f, r'''          description: Utilisateur trouvé
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/User"
        "401":
          $ref: "#/components/responses/Unauthorized"
        "404":
''', r'''          description: Utilisateur trouvé
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/User"
        "401":
          $ref: "#/components/responses/Unauthorized"
        "403":
          $ref: "#/components/responses/Forbidden"
        "404":
''', "403 sur GET /users/{id}", skip_if='$ref: "#/components/responses/Forbidden"\n        "404":\n          $ref: "#/components/responses/NotFound"\n      security:\n        - bearerAuth: []\n    put:')

    # PUT /users/{id}
    sub(f, "      summary: Mettre à jour les informations d'un utilisateur\n      operationId: updateUser\n",
        "      summary: Mettre à jour les informations d'un utilisateur\n"
        "      description: Mise à jour partielle (les champs absents sont conservés). Seul un ADMINISTRATEUR "
        "peut modifier l'email (403 sinon, 409 si déjà utilisé).\n"
        "      operationId: updateUser\n",
        "description PUT /users/{id}", skip_if="Mise à jour partielle")
    sub(f, r'''          description: Utilisateur mis à jour
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/User"
        "400":
          $ref: "#/components/responses/BadRequest"
        "401":
          $ref: "#/components/responses/Unauthorized"
        "403":
          $ref: "#/components/responses/Forbidden"
        "404":
          $ref: "#/components/responses/NotFound"
''', r'''          description: Utilisateur mis à jour
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/User"
        "400":
          $ref: "#/components/responses/BadRequest"
        "401":
          $ref: "#/components/responses/Unauthorized"
        "403":
          $ref: "#/components/responses/Forbidden"
        "404":
          $ref: "#/components/responses/NotFound"
        "409":
          $ref: "#/components/responses/Conflict"
''', "409 sur PUT /users/{id}", skip_if="description: Utilisateur mis à jour\n          content:\n            application/json:\n              schema:\n                $ref: \"#/components/schemas/User\"\n        \"400\":\n          $ref: \"#/components/responses/BadRequest\"\n        \"401\":\n          $ref: \"#/components/responses/Unauthorized\"\n        \"403\":\n          $ref: \"#/components/responses/Forbidden\"\n        \"404\":\n          $ref: \"#/components/responses/NotFound\"\n        \"409\":")

    # DELETE /users/{id}
    sub(f, r'''          description: Utilisateur supprimé
        "401":
          $ref: "#/components/responses/Unauthorized"
        "403":
          $ref: "#/components/responses/Forbidden"
        "404":
          $ref: "#/components/responses/NotFound"
''', r'''          description: Utilisateur supprimé
        "401":
          $ref: "#/components/responses/Unauthorized"
        "403":
          $ref: "#/components/responses/Forbidden"
        "404":
          $ref: "#/components/responses/NotFound"
        "409":
          $ref: "#/components/responses/Conflict"
''', "409 sur DELETE /users/{id}",
        skip_if='description: Utilisateur supprimé\n        "401":\n          $ref: "#/components/responses/Unauthorized"\n        "403":\n          $ref: "#/components/responses/Forbidden"\n        "404":\n          $ref: "#/components/responses/NotFound"\n        "409":')

    # PATCH /users/{id}/status
    sub(f, "      summary: Modifier le statut d'un utilisateur (activation/désactivation/suspension)\n      operationId: updateUserStatus\n",
        "      summary: Modifier le statut d'un utilisateur (activation/désactivation/suspension)\n"
        "      description: Passer à ACTIF lève aussi un éventuel verrouillage. Passer à INACTIF ou SUSPENDU révoque les sessions.\n"
        "      operationId: updateUserStatus\n",
        "description PATCH status", skip_if="Passer à ACTIF lève aussi")
    sub(f, r'''          description: Statut mis à jour
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/User"
        "401":
''', r'''          description: Statut mis à jour
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/User"
        "400":
          $ref: "#/components/responses/BadRequest"
        "401":
''', "400 sur PATCH status",
        skip_if='description: Statut mis à jour\n          content:\n            application/json:\n              schema:\n                $ref: "#/components/schemas/User"\n        "400":')
    sub(f, r'''        "404":
          $ref: "#/components/responses/NotFound"
      security:
        - bearerAuth: []
  /roles:
''', r'''        "404":
          $ref: "#/components/responses/NotFound"
        "409":
          $ref: "#/components/responses/Conflict"
      security:
        - bearerAuth: []
  /roles:
''', "409 sur PATCH status", skip_if='"409":\n          $ref: "#/components/responses/Conflict"\n      security:\n        - bearerAuth: []\n  /roles:')

    # schémas
    sub(f, "    RegisterRequest:\n", r'''    UserCreateRequest:
      required:
        - email
        - firstName
        - lastName
        - role
      type: object
      properties:
        lastName:
          type: string
        firstName:
          type: string
        email:
          type: string
          format: email
        phone:
          type: string
        role:
          $ref: "#/components/schemas/RoleName"
    ChangePasswordRequest:
      required:
        - currentPassword
        - newPassword
      type: object
      properties:
        currentPassword:
          type: string
          format: password
        newPassword:
          minLength: 8
          type: string
          format: password
    RegisterRequest:
''', "schémas UserCreateRequest, ChangePasswordRequest", skip_if="    UserCreateRequest:")

    sub(f, "  parameters:\n    UserId:\n", r'''    Conflict:
      description: Conflit avec l'état actuel de la ressource
      content:
        application/json:
          schema:
            $ref: "#/components/schemas/ErrorResponse"
  parameters:
    UserId:
''', "réponse Conflict", skip_if="    Conflict:")

    sub("contracts/CHANGELOG.md", "### user-service\n", r'''### user-service
- **Ajout** du rôle `ENTREPRISE` dans `RoleName`.
- **Ajout** du schéma partagé `Gender` (remplace les enums inline de `User` et `UserUpdateRequest`).
- **Ajout** de `POST /auth/change-password` (`changePassword`, `ChangePasswordRequest`) et de `POST /users` (`createUser`, `UserCreateRequest`).
- **Précisions** : 423 sur `/auth/login` ; 403 sur `GET /users/{id}` (soi-même ou ADMINISTRATEUR) ; 400/409 sur `PATCH /users/{id}/status` ; 409 sur `PUT`/`DELETE /users/{id}` ; changement d'email réservé à ADMINISTRATEUR ; réponse `Conflict`.
- SPI `user-service-spi` passé en **1.1.0**.
''', "entrées user-service", skip_if="`ENTREPRISE` dans `RoleName`")


# ----------------------------------------------------------------------------
# 2. pom, config, env, docker, k8s, scripts, docs
# ----------------------------------------------------------------------------
def config_and_infra():
    pom = "user-service/pom.xml"
    sub(pom, "    <alumni.spi.version>1.0.0</alumni.spi.version>\n",
        "    <alumni.spi.version>1.1.0</alumni.spi.version>\n    <jjwt.version>0.12.6</jjwt.version>\n",
        "version SPI 1.1.0 + jjwt.version", skip_if="<jjwt.version>")
    sub(pom, "    <!-- Envoi d'emails (confirmation de compte, réinitialisation de mot de passe) -->\n", r'''    <!-- JWT (émission et validation des access tokens) -->
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>${jjwt.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>

    <!-- Envoi d'emails (confirmation de compte, réinitialisation de mot de passe) -->
''', "dépendances jjwt", skip_if="jjwt-api")

    sub(RES + "/application.yaml", r'''jwt:
  # Base64, 32 octets minimum
  secret: ${JWT_SECRET}
  issuer: ${JWT_ISSUER}
''', r'''jwt:
  # Base64, 32 octets minimum
  secret: ${JWT_SECRET}
  issuer: ${JWT_ISSUER}
  # Durée de vie de l'access token (format Spring : 15m, 1h)
  access-ttl: ${JWT_ACCESS_TTL}

auth:
  refresh-ttl: ${REFRESH_TOKEN_TTL}
  password-reset-ttl: ${PASSWORD_RESET_TTL}
  max-failed-attempts: ${MAX_FAILED_ATTEMPTS}
  lock-duration: ${LOCK_DURATION}
  # Premier ADMINISTRATEUR créé au démarrage s'il n'en existe aucun d'actif (ignoré si email ou mot de passe vide)
  bootstrap-admin:
    email: ${BOOTSTRAP_ADMIN_EMAIL:}
    password: ${BOOTSTRAP_ADMIN_PASSWORD:}
    first-name: ${BOOTSTRAP_ADMIN_FIRST_NAME}
    last-name: ${BOOTSTRAP_ADMIN_LAST_NAME}
''', "bloc jwt.access-ttl + auth", skip_if="access-ttl:")

    append("user-service/.env.example", r'''
# --- Authentification ---
JWT_ACCESS_TTL=15m
REFRESH_TOKEN_TTL=7d
PASSWORD_RESET_TTL=1h
MAX_FAILED_ATTEMPTS=5
LOCK_DURATION=15m
# Premier ADMINISTRATEUR (créé au démarrage s'il n'y en a aucun d'actif).
# make setup génère le mot de passe ; il refuse CHANGE_ME.
BOOTSTRAP_ADMIN_EMAIL=admin@alumni-uasz.sn
BOOTSTRAP_ADMIN_PASSWORD=CHANGE_ME
BOOTSTRAP_ADMIN_FIRST_NAME=Admin
BOOTSTRAP_ADMIN_LAST_NAME=Alumni UASZ
''', ".env.example : variables d'authentification", skip_if="JWT_ACCESS_TTL=")

    sub("user-service/docker-compose.yml",
        "      EMAIL_CONFIRMATION_TTL: ${EMAIL_CONFIRMATION_TTL:?EMAIL_CONFIRMATION_TTL manquant}\n",
        "      EMAIL_CONFIRMATION_TTL: ${EMAIL_CONFIRMATION_TTL:?EMAIL_CONFIRMATION_TTL manquant}\n" + r'''      JWT_ACCESS_TTL: ${JWT_ACCESS_TTL:?JWT_ACCESS_TTL manquant}
      REFRESH_TOKEN_TTL: ${REFRESH_TOKEN_TTL:?REFRESH_TOKEN_TTL manquant}
      PASSWORD_RESET_TTL: ${PASSWORD_RESET_TTL:?PASSWORD_RESET_TTL manquant}
      MAX_FAILED_ATTEMPTS: ${MAX_FAILED_ATTEMPTS:?MAX_FAILED_ATTEMPTS manquant}
      LOCK_DURATION: ${LOCK_DURATION:?LOCK_DURATION manquant}
      BOOTSTRAP_ADMIN_EMAIL: ${BOOTSTRAP_ADMIN_EMAIL:-}
      BOOTSTRAP_ADMIN_PASSWORD: ${BOOTSTRAP_ADMIN_PASSWORD:-}
      BOOTSTRAP_ADMIN_FIRST_NAME: ${BOOTSTRAP_ADMIN_FIRST_NAME:?BOOTSTRAP_ADMIN_FIRST_NAME manquant}
      BOOTSTRAP_ADMIN_LAST_NAME: ${BOOTSTRAP_ADMIN_LAST_NAME:?BOOTSTRAP_ADMIN_LAST_NAME manquant}
''', "docker-compose : variables d'authentification", skip_if="JWT_ACCESS_TTL:")

    sub("user-service/k8s/base/configmap.yaml", "  EMAIL_CONFIRMATION_TTL: 24h",
        "  EMAIL_CONFIRMATION_TTL: 24h\n" + r'''  JWT_ACCESS_TTL: 15m
  REFRESH_TOKEN_TTL: 7d
  PASSWORD_RESET_TTL: 1h
  MAX_FAILED_ATTEMPTS: "5"
  LOCK_DURATION: 15m
  BOOTSTRAP_ADMIN_EMAIL: admin@alumni-uasz.sn
  BOOTSTRAP_ADMIN_FIRST_NAME: Admin
  BOOTSTRAP_ADMIN_LAST_NAME: Alumni UASZ''', "ConfigMap : variables d'authentification", skip_if="JWT_ACCESS_TTL:")

    sub("user-service/scripts/k8s-secrets.sh", r'''cat > "$D/app.env" <<EOF
JWT_SECRET=${JWT_SECRET}
EOF''', r'''cat > "$D/app.env" <<EOF
JWT_SECRET=${JWT_SECRET}
BOOTSTRAP_ADMIN_PASSWORD=${BOOTSTRAP_ADMIN_PASSWORD}
EOF''', "k8s-secrets : BOOTSTRAP_ADMIN_PASSWORD", skip_if="BOOTSTRAP_ADMIN_PASSWORD=")

    sub("user-service/k8s/overlays/dev/kustomization.yaml", "#   app.env : JWT_SECRET\n",
        "#   app.env : JWT_SECRET, BOOTSTRAP_ADMIN_PASSWORD\n", "commentaire app.env", skip_if="JWT_SECRET, BOOTSTRAP_ADMIN_PASSWORD")
    sub("user-service/k8s/overlays/prod/kustomization.yaml",
        "#   user-service-secrets : JWT_SECRET (IDENTIQUE dans tous les services)",
        "#   user-service-secrets : JWT_SECRET (IDENTIQUE dans tous les services), BOOTSTRAP_ADMIN_PASSWORD",
        "commentaire secrets prod", skip_if="BOOTSTRAP_ADMIN_PASSWORD")

    g = "user-service/scripts/gen-env.sh"
    sub(g, "SECRET_KEYS=(POSTGRES_PASSWORD JWT_SECRET)",
        "SECRET_KEYS=(POSTGRES_PASSWORD JWT_SECRET BOOTSTRAP_ADMIN_PASSWORD)",
        "SECRET_KEYS", skip_if="JWT_SECRET BOOTSTRAP_ADMIN_PASSWORD)")
    sub(g, "example_keys() {\n", r'''# Mot de passe de l'admin initial : conforme à la politique (lettre + chiffre, 8 à 72 octets)
new_admin_password() { printf '%sAa1' "$(openssl rand -base64 18 | tr -d '=+/\n')"; }

example_keys() {
''', "fonction new_admin_password", skip_if="new_admin_password()")
    sub(g, 'set_var JWT_SECRET "$JWT_VALUE"\n', r'''set_var JWT_SECRET "$JWT_VALUE"
set_var BOOTSTRAP_ADMIN_PASSWORD "$(new_admin_password)"
info "Mot de passe de l'administrateur initial : voir BOOTSTRAP_ADMIN_PASSWORD dans .env"
''', "génération à la création", skip_if='set_var BOOTSTRAP_ADMIN_PASSWORD "$(new_admin_password)"\ninfo')
    sub(g, r'''    if [ "$(get_var .env POSTGRES_PASSWORD)" = "CHANGE_ME" ]; then
        warn "POSTGRES_PASSWORD vaut CHANGE_ME : lancez 'make env' puis 'make reset-db'"
    fi
''', r'''    if [ "$(get_var .env POSTGRES_PASSWORD)" = "CHANGE_ME" ]; then
        warn "POSTGRES_PASSWORD vaut CHANGE_ME : lancez 'make env' puis 'make reset-db'"
    fi
    if [[ "$(get_var .env BOOTSTRAP_ADMIN_PASSWORD)" == CHANGE_ME* ]]; then
        set_var BOOTSTRAP_ADMIN_PASSWORD "$(new_admin_password)"; info "BOOTSTRAP_ADMIN_PASSWORD généré"
    fi
''', "génération en mode --complete", skip_if='"$(get_var .env BOOTSTRAP_ADMIN_PASSWORD)"')

    sub("user-service/README.md",
        "- `loginUser`, `refreshToken`, `logoutUser` (AL-19) et `forgotPassword`, `resetPassword` (AL-20) répondent 501 dans `AuthController` en attendant leur implémentation.",
        "- Authentification (AL-19) et mot de passe (AL-20) sont implémentés : login (verrouillage après N échecs), refresh token avec rotation et "
        "détection de réutilisation, logout, forgot/reset/change-password, administration des utilisateurs. "
        "Le premier ADMINISTRATEUR est créé au démarrage depuis `BOOTSTRAP_ADMIN_*`.",
        "README : section AL-19/AL-20", skip_if="Authentification (AL-19) et mot de passe (AL-20) sont implémentés")

    put(RES + "/db/migration/V3__auth_login_tokens_audit.sql", r'''
-- AL-19 / AL-20 : connexion, refresh tokens, mot de passe oublié, verrouillage, audit.

ALTER TABLE users
    ADD COLUMN phone                 VARCHAR(30),
    ADD COLUMN gender                VARCHAR(10),
    ADD COLUMN birth_date            DATE,
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_login_at         TIMESTAMP WITH TIME ZONE;

ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role
    CHECK (role IN ('ALUMNI', 'ETUDIANT', 'ENSEIGNANT', 'ENTREPRISE', 'ADMINISTRATEUR'));
ALTER TABLE users ADD CONSTRAINT ck_users_gender
    CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'));

-- Refresh tokens : seule l'empreinte SHA-256 est stockée. Un jeton révoqué reste en base jusqu'à son
-- expiration pour détecter sa réutilisation (vol possible).
CREATE TABLE refresh_tokens (
    id         UUID                     PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)              NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- Mot de passe oublié ET invitation d'un compte créé par un administrateur.
CREATE TABLE password_reset_tokens (
    id         UUID                     PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)              NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);

-- Journal d'audit : pas de FK vers users (il survit à la suppression d'un compte).
CREATE TABLE audit_logs (
    id         UUID                     PRIMARY KEY,
    actor_id   UUID,
    action     VARCHAR(60)              NOT NULL,
    target_id  UUID,
    details    VARCHAR(500),
    ip_address VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id);
''')


# ----------------------------------------------------------------------------
# 3. Code Java
# ----------------------------------------------------------------------------
def exc(name, status, code, msg, doc):
    return ("package com.uasz.alumni.user.exceptions;\n\n"
            "import org.springframework.http.HttpStatus;\n\n"
            f"/** {doc} */\n"
            f"public class {name} extends ApiException {{\n"
            f"    public {name}() {{\n"
            f'        super(HttpStatus.{status}, "{code}", "{msg}");\n'
            "    }\n}\n")


def java_exceptions():
    d = BASE + "/exceptions/"
    table = [
        ("InvalidCredentialsException", "UNAUTHORIZED", "INVALID_CREDENTIALS", "Email ou mot de passe incorrect.",
         "401 : email inconnu ou mot de passe incorrect (un seul message : pas d'énumération de comptes)."),
        ("AccountNotActivatedException", "FORBIDDEN", "ACCOUNT_NOT_ACTIVATED",
         "Votre compte n'est pas encore activé. Confirmez votre adresse email.",
         "403 : l'adresse email n'a pas encore été confirmée."),
        ("AccountDisabledException", "FORBIDDEN", "ACCOUNT_DISABLED", "Votre compte est désactivé ou suspendu.",
         "403 : compte INACTIF ou SUSPENDU."),
        ("AccountLockedException", "LOCKED", "ACCOUNT_LOCKED",
         "Compte temporairement verrouillé après trop d'échecs de connexion.", "423 : trop d'échecs de connexion."),
        ("InvalidRefreshTokenException", "UNAUTHORIZED", "INVALID_REFRESH_TOKEN", "Refresh token invalide ou expiré.",
         "401 : refresh token inconnu, expiré, révoqué ou compte non actif."),
        ("InvalidResetTokenException", "BAD_REQUEST", "INVALID_OR_EXPIRED_TOKEN",
         "Le lien de réinitialisation est invalide ou a expiré.",
         "400 : jeton de réinitialisation inconnu, expiré ou déjà utilisé."),
        ("InvalidCurrentPasswordException", "BAD_REQUEST", "INVALID_CURRENT_PASSWORD",
         "Le mot de passe actuel est incorrect.", "400 : le mot de passe actuel fourni est incorrect."),
        ("UserNotFoundException", "NOT_FOUND", "USER_NOT_FOUND", "Utilisateur introuvable.", "404 : utilisateur introuvable."),
    ]
    for t in table:
        put(d + t[0] + ".java", exc(*t))

    put(d + "WeakPasswordException.java", r'''
package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** Mot de passe non conforme : 422 pour reset/change (contrat), 400 pour l'inscription. */
public class WeakPasswordException extends ApiException {
    public WeakPasswordException(HttpStatus status) {
        super(status, "WEAK_PASSWORD",
                "Le mot de passe doit faire de 8 à 72 caractères et contenir au moins une lettre et un chiffre.");
    }
}
''')
    put(d + "ForbiddenException.java", r'''
package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 403 : permissions insuffisantes. */
public class ForbiddenException extends ApiException {
    public ForbiddenException() {
        this("Accès refusé : permissions insuffisantes.");
    }

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}
''')


def java_config():
    d = BASE + "/config/"
    put(d + "JwtProperties.java", r'''
package com.uasz.alumni.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, String issuer, Duration accessTtl) {
}
''')
    put(d + "AuthProperties.java", r'''
package com.uasz.alumni.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres d'authentification (voir application.yaml, valeurs fournies par .env / ConfigMap). */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Duration refreshTtl,
                             Duration passwordResetTtl,
                             int maxFailedAttempts,
                             Duration lockDuration,
                             BootstrapAdmin bootstrapAdmin) {

    public record BootstrapAdmin(String email, String password, String firstName, String lastName) {
    }
}
''')
    put(d + "AppConfig.java", r'''
package com.uasz.alumni.user.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Beans transverses : propriétés, horloge (injectable dans les tests), hachage des mots de passe. */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, JwtProperties.class, AuthProperties.class})
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // coût 12 ; les anciens hashes (coût 10) restent valides
        return new BCryptPasswordEncoder(12);
    }
}
''')
    put(d + "SecurityConfig.java", r'''
package com.uasz.alumni.user.config;

import com.uasz.alumni.user.jwt.JwtAuthenticationFilter;
import com.uasz.alumni.user.jwt.JwtService;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.RestSecurityErrorHandler;
import com.uasz.alumni.user.spi.model.RoleName;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API stateless. Les règles "ADMINISTRATEUR uniquement" sont ici (visibles d'un coup d'œil) ;
 * les règles "soi-même ou ADMINISTRATEUR" sont dans UserService (elles dépendent de l'id ciblé).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN = RoleName.ADMINISTRATEUR.name();

    private final String apiPrefix;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${security.api.prefix}") String apiPrefix,
            @Value("${security.cors.allowed-origins}") String allowedOrigins) {
        this.apiPrefix = apiPrefix;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtService jwtService,
                                            UserAccountRepository users,
                                            Clock clock,
                                            RestSecurityErrorHandler errorHandler) throws Exception {

        String[] publicPosts = Stream.of(
                        "/auth/register", "/auth/login", "/auth/refresh-token",
                        "/auth/forgot-password", "/auth/reset-password",
                        "/auth/confirm-email", "/auth/resend-confirmation")
                .map(p -> apiPrefix + p)
                .toArray(String[]::new);

        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.POST, publicPosts).permitAll()
                // ADMINISTRATEUR uniquement
                .requestMatchers(HttpMethod.GET, apiPrefix + "/users").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, apiPrefix + "/users").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PATCH, apiPrefix + "/users/*/status").hasRole(ADMIN)
                .requestMatchers(HttpMethod.DELETE, apiPrefix + "/users/*").hasRole(ADMIN)
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, users, clock),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
''')
    put(d + "AdminBootstrap.java", r'''
package com.uasz.alumni.user.config;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.service.AuditService;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.PasswordPolicy;
import com.uasz.alumni.user.utils.TextUtils;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crée le premier ADMINISTRATEUR au démarrage s'il n'en existe aucun d'actif. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthProperties props;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Clock clock;

    public AdminBootstrap(AuthProperties props, UserAccountRepository users, PasswordEncoder encoder,
                          AuditService audit, Clock clock) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin cfg = props.bootstrapAdmin();
        if (cfg == null || isBlank(cfg.email()) || isBlank(cfg.password())) {
            log.info("Bootstrap ADMINISTRATEUR ignoré (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD vides)");
            return;
        }
        if (cfg.password().startsWith("CHANGE_ME") || !PasswordPolicy.isValid(cfg.password())) {
            log.error("Bootstrap ADMINISTRATEUR ignoré : mot de passe CHANGE_ME ou non conforme "
                    + "(8 à 72 octets, une lettre et un chiffre). Lancez 'make env-complete'.");
            return;
        }
        if (users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF) > 0) {
            return;
        }
        UserAccount admin = new UserAccount(
                TextUtils.normalizeEmail(cfg.email()),
                encoder.encode(cfg.password()),
                cfg.firstName().trim(),
                cfg.lastName().trim(),
                RoleName.ADMINISTRATEUR,
                UserStatus.EN_ATTENTE);
        admin.activate(clock.instant());
        try {
            users.saveAndFlush(admin);
            audit.record("ADMIN_BOOTSTRAPPED", null, admin.getId(), null);
            log.warn("Compte ADMINISTRATEUR initial créé ({}). Changez son mot de passe dès la première connexion.",
                    admin.getEmail());
        } catch (DataIntegrityViolationException e) {
            // ex. 2 réplicas qui démarrent en même temps : l'autre a gagné la course
            log.warn("Bootstrap ADMINISTRATEUR non effectué : {}", e.getMostSpecificCause().getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
''')


def java_model_repo():
    d = BASE + "/model/"
    put(d + "UserAccount.java", r'''
package com.uasz.alumni.user.model;

import com.uasz.alumni.user.spi.model.Gender;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compte utilisateur. Nommé {@code UserAccount} pour ne pas être confondu avec
 * le modèle d'API {@code User} généré depuis le contrat.
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleName role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
        // requis par JPA
    }

    public UserAccount(String email, String passwordHash, String firstName, String lastName,
                       RoleName role, UserStatus status) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Active le compte après confirmation de l'adresse email (ou définition du mot de passe par lien). */
    public void activate(Instant verifiedAt) {
        this.status = UserStatus.ACTIF;
        this.emailVerifiedAt = verifiedAt;
    }

    public void changeStatus(UserStatus newStatus) {
        this.status = newStatus;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    /** Mise à jour partielle : un argument null laisse la valeur inchangée ; un téléphone vide l'efface. */
    public void updateProfile(String firstName, String lastName, String phone, Gender gender, LocalDate birthDate) {
        if (firstName != null) {
            this.firstName = firstName;
        }
        if (lastName != null) {
            this.lastName = lastName;
        }
        if (phone != null) {
            this.phone = phone.isEmpty() ? null : phone;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }

    // ───── verrouillage après échecs de connexion ─────

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(int maxAttempts, Duration lockDuration, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void clearLock() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public RoleName getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public String getPhone() { return phone; }
    public Gender getGender() { return gender; }
    public LocalDate getBirthDate() { return birthDate; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
''')
    put(d + "RefreshToken.java", r'''
package com.uasz.alumni.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Refresh token opaque. Seule l'empreinte SHA-256 est conservée. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // requis par JPA
    }

    public RefreshToken(UserAccount user, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
''')
    put(d + "PasswordResetToken.java", r'''
package com.uasz.alumni.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Jeton de réinitialisation / définition de mot de passe. Seule l'empreinte SHA-256 est conservée. */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetToken() {
        // requis par JPA
    }

    public PasswordResetToken(UserAccount user, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
''')
    put(d + "AuditLog.java", r'''
package com.uasz.alumni.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Entrée de journal d'audit : immuable, sans FK vers users. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(length = 500)
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // requis par JPA
    }

    public AuditLog(UUID actorId, String action, UUID targetId, String details, String ipAddress) {
        this.actorId = actorId;
        this.action = action;
        this.targetId = targetId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public UUID getTargetId() { return targetId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
''')

    r = BASE + "/repository/"
    put(r + "UserAccountRepository.java", r'''
package com.uasz.alumni.user.repository;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** L'email est toujours stocké en minuscules : voir {@code TextUtils.normalizeEmail}. */
    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmail(String email);

    long countByRoleAndStatus(RoleName role, UserStatus status);

    Page<UserAccount> findByRole(RoleName role, Pageable pageable);

    Page<UserAccount> findByStatus(UserStatus status, Pageable pageable);

    Page<UserAccount> findByRoleAndStatus(RoleName role, UserStatus status, Pageable pageable);
}
''')
    put(r + "RefreshTokenRepository.java", r'''
package com.uasz.alumni.user.repository;

import com.uasz.alumni.user.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("hash") String hash);

    /** Ferme toutes les sessions d'un utilisateur (mot de passe changé, compte désactivé, vol détecté). */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :limit")
    int deleteExpired(@Param("limit") Instant limit);
}
''')
    put(r + "PasswordResetTokenRepository.java", r'''
package com.uasz.alumni.user.repository;

import com.uasz.alumni.user.model.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHashWithUser(@Param("hash") String hash);

    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Un seul jeton actif par utilisateur : on invalide les anciens avant d'en émettre un nouveau. */
    @Modifying(flushAutomatically = true)
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :limit")
    int deleteExpired(@Param("limit") Instant limit);
}
''')
    put(r + "AuditLogRepository.java", r'''
package com.uasz.alumni.user.repository;

import com.uasz.alumni.user.model.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
''')


def java_utils_security():
    u = BASE + "/utils/"
    put(u + "TextUtils.java", r'''
package com.uasz.alumni.user.utils;

import java.util.Locale;

public final class TextUtils {

    private TextUtils() {
    }

    /** trim + minuscules : les emails sont toujours stockés normalisés. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
''')
    put(u + "PasswordPolicy.java", r'''
package com.uasz.alumni.user.utils;

import java.nio.charset.StandardCharsets;

/** 8 à 72 octets (limite de BCrypt), au moins une lettre et un chiffre. */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        return password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
    }
}
''')
    put(u + "ClientIp.java", r'''
package com.uasz.alumni.user.utils;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientIp {

    private ClientIp() {
    }

    /**
     * IP de la requête courante. Derrière l'ingress de confiance, FORWARD_HEADERS_STRATEGY=native
     * fournit la vraie IP (on ne lit jamais un en-tête X-Forwarded-For falsifiable à la main).
     */
    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
''')
    put(u + "UserMapper.java", r'''
package com.uasz.alumni.user.utils;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.spi.model.User;
import java.time.ZoneOffset;

/** Conversion entité -> modèle d'API. Le hash du mot de passe n'est jamais exposé. */
public final class UserMapper {

    private UserMapper() {
    }

    public static User toApi(UserAccount account) {
        return new User()
                .id(account.getId())
                .email(account.getEmail())
                .firstName(account.getFirstName())
                .lastName(account.getLastName())
                .role(account.getRole())
                .status(account.getStatus())
                .phone(account.getPhone())
                .gender(account.getGender())
                .birthDate(account.getBirthDate())
                .createdAt(account.getCreatedAt() == null ? null : account.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
''')

    j = BASE + "/jwt/"
    put(j + "JwtService.java", r'''
package com.uasz.alumni.user.jwt;

import com.uasz.alumni.user.config.JwtProperties;
import com.uasz.alumni.user.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Émission et validation des access tokens (JWT HMAC). Claims : sub = id utilisateur (UUID),
 * role, email. Les autres microservices valident signature + émetteur avec le même JWT_SECRET / JWT_ISSUER.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        String secret = props.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) manquant : lancez 'make setup'");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) doit être encodé en Base64", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret doit faire au moins 32 octets une fois décodé");
        }
        if (props.issuer() == null || props.issuer().isBlank()) {
            throw new IllegalStateException("jwt.issuer (JWT_ISSUER) manquant");
        }
        if (props.accessTtl() == null || props.accessTtl().isZero() || props.accessTtl().isNegative()) {
            throw new IllegalStateException("jwt.access-ttl (JWT_ACCESS_TTL) invalide");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = props.issuer();
        this.accessTtl = props.accessTtl();
        this.clock = clock;
    }

    public String generateAccessToken(UserAccount user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Id utilisateur si le jeton est valide (signature, émetteur, expiration), sinon vide. */
    public Optional<UUID> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
''')
    put(j + "JwtAuthenticationFilter.java", r'''
package com.uasz.alumni.user.jwt;

import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.AuthenticatedUser;
import com.uasz.alumni.user.spi.model.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Jeton invalide ou compte non actif : on n'authentifie simplement pas (401 via RestSecurityErrorHandler
 * sur les routes protégées). Le rôle vient de la BASE, pas du jeton : une suspension est effective
 * immédiatement dans user-service. Instancié dans SecurityConfig (pas un @Component : évite la double inscription).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository users;
    private final Clock clock;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository users, Clock clock) {
        this.jwtService = jwtService;
        this.users = users;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            jwtService.parseUserId(header.substring(7).trim())
                    .flatMap(users::findById)
                    .filter(u -> u.getStatus() == UserStatus.ACTIF && !u.isLockedAt(clock.instant()))
                    .ifPresent(u -> {
                        AuthenticatedUser principal = new AuthenticatedUser(u.getId(), u.getEmail(), u.getRole());
                        var auth = UsernamePasswordAuthenticationToken.authenticated(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(auth);
                        SecurityContextHolder.setContext(context);
                    });
        }
        chain.doFilter(request, response);
    }
}
''')

    s = BASE + "/security/"
    put(s + "AuthenticatedUser.java", r'''
package com.uasz.alumni.user.security;

import com.uasz.alumni.user.spi.model.RoleName;
import java.util.UUID;

/** Principal Spring Security : léger, sans entité JPA. */
public record AuthenticatedUser(UUID id, String email, RoleName role) {
}
''')
    put(s + "CurrentUser.java", r'''
package com.uasz.alumni.user.security;

import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.ForbiddenException;
import com.uasz.alumni.user.spi.model.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Accès à l'utilisateur authentifié de la requête courante. */
@Component
public class CurrentUser {

    public Optional<AuthenticatedUser> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public AuthenticatedUser require() {
        return principal().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentification requise."));
    }

    public boolean isAdmin() {
        return principal().map(u -> u.role() == RoleName.ADMINISTRATEUR).orElse(false);
    }

    /** Autorise l'utilisateur lui-même ou un ADMINISTRATEUR, sinon 403. */
    public void requireSelfOrAdmin(UUID targetId) {
        AuthenticatedUser me = require();
        if (!me.id().equals(targetId) && me.role() != RoleName.ADMINISTRATEUR) {
            throw new ForbiddenException();
        }
    }
}
''')
    put(s + "RestSecurityErrorHandler.java", r'''
package com.uasz.alumni.user.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 401 / 403 produits par Spring Security (avant d'atteindre un controller), au format ErrorResponse
 * du contrat : {code, message, timestamp}. Messages sans guillemets doubles (JSON écrit à la main).
 */
@Component
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, 401, "UNAUTHORIZED", "Authentification requise ou invalide.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, 403, "ACCESS_DENIED", "Accès refusé : permissions insuffisantes.");
    }

    private static void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}"
                .formatted(code, message, OffsetDateTime.now()));
    }
}
''')


def java_services():
    d = BASE + "/service/"
    put(d + "AuditService.java", r'''
package com.uasz.alumni.user.service;

import com.uasz.alumni.user.model.AuditLog;
import com.uasz.alumni.user.repository.AuditLogRepository;
import com.uasz.alumni.user.utils.ClientIp;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Journal d'audit (table audit_logs + log applicatif). Participe à la transaction appelante. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(String action, UUID actorId, UUID targetId, String details) {
        String safeDetails = details != null && details.length() > 500 ? details.substring(0, 500) : details;
        String ip = ClientIp.current();
        repo.save(new AuditLog(actorId, action, targetId, safeDetails, ip));
        log.info("action={} actor={} target={} ip={}", action, actorId, targetId, ip);
    }
}
''')
    put(d + "AuthSession.java", r'''
package com.uasz.alumni.user.service;

import com.uasz.alumni.user.model.UserAccount;

/** Résultat d'une connexion ou d'un rafraîchissement ; converti en AuthResponse par le controller. */
public record AuthSession(String accessToken, String refreshToken, long expiresInSeconds, UserAccount user) {
}
''')
    put(d + "RefreshTokenService.java", r'''
package com.uasz.alumni.user.service;

import com.uasz.alumni.user.config.AuthProperties;
import com.uasz.alumni.user.model.RefreshToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.RefreshTokenRepository;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Émission, recherche et révocation des refresh tokens (jamais stockés en clair). */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final AuthProperties props;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repo, AuthProperties props, Clock clock) {
        this.repo = repo;
        this.props = props;
        this.clock = clock;
    }

    /** Crée un refresh token et retourne sa valeur EN CLAIR (à renvoyer au client, jamais relue ensuite). */
    @Transactional
    public String issue(UserAccount user) {
        Instant now = clock.instant();
        String raw = TokenUtils.generateToken();
        repo.save(new RefreshToken(user, TokenUtils.sha256Hex(raw), now, now.plus(props.refreshTtl())));
        return raw;
    }

    public Optional<RefreshToken> find(String rawToken) {
        return repo.findByTokenHashWithUser(TokenUtils.sha256Hex(rawToken.trim()));
    }

    /** Révoque ce jeton s'il appartient bien à l'utilisateur ; sinon ignore sans erreur. */
    @Transactional
    public void revoke(UUID userId, String rawToken) {
        Instant now = clock.instant();
        find(rawToken)
                .filter(t -> t.getUser().getId().equals(userId))
                .ifPresent(t -> t.revoke(now));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repo.revokeAllByUserId(userId, clock.instant());
    }
}
''')
    put(d + "AuthService.java", r'''
package com.uasz.alumni.user.service;

import com.uasz.alumni.user.config.AuthProperties;
import com.uasz.alumni.user.exceptions.AccountDisabledException;
import com.uasz.alumni.user.exceptions.AccountLockedException;
import com.uasz.alumni.user.exceptions.AccountNotActivatedException;
import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.InvalidCredentialsException;
import com.uasz.alumni.user.exceptions.InvalidRefreshTokenException;
import com.uasz.alumni.user.jwt.JwtService;
import com.uasz.alumni.user.model.RefreshToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.TextUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AL-19 : connexion, rafraîchissement du jeton, déconnexion. */
@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final AuthProperties props;
    private final Clock clock;

    /** Hash factice : un email inconnu coûte le même temps de calcul qu'un mauvais mot de passe. */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwt,
                       RefreshTokenService refreshTokens, AuditService audit, AuthProperties props, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * noRollbackFor : le compteur d'échecs et l'audit doivent être enregistrés même quand la méthode
     * se termine par une ApiException (401/403/423).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthSession login(String rawEmail, String password) {
        Instant now = clock.instant();
        String email = TextUtils.normalizeEmail(rawEmail);

        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            audit.record("LOGIN_FAILED", null, null, "email=" + email);
            throw new InvalidCredentialsException();
        }
        if (user.isLockedAt(now)) {
            audit.record("LOGIN_LOCKED", user.getId(), user.getId(), null);
            throw new AccountLockedException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(props.maxFailedAttempts(), props.lockDuration(), now);
            audit.record("LOGIN_FAILED", user.getId(), user.getId(), null);
            throw new InvalidCredentialsException();
        }

        // Le statut n'est révélé qu'à quelqu'un qui connaît le bon mot de passe.
        switch (user.getStatus()) {
            case EN_ATTENTE -> throw new AccountNotActivatedException();
            case INACTIF, SUSPENDU -> throw new AccountDisabledException();
            case ACTIF -> { }
        }

        user.registerSuccessfulLogin(now);
        audit.record("LOGIN", user.getId(), user.getId(), null);
        return session(user, refreshTokens.issue(user));
    }

    /**
     * Rotation : l'ancien refresh token est révoqué et un nouveau est émis. Présenter un jeton DÉJÀ
     * révoqué indique une réutilisation (vol possible) : toutes les sessions de l'utilisateur sont fermées.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthSession refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.find(rawRefreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
        UserAccount user = token.getUser();

        if (token.isRevoked()) {
            refreshTokens.revokeAll(user.getId());
            audit.record("REFRESH_REUSE_DETECTED", user.getId(), user.getId(), null);
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpiredAt(now) || user.getStatus() != UserStatus.ACTIF || user.isLockedAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        token.revoke(now);
        audit.record("TOKEN_REFRESHED", user.getId(), user.getId(), null);
        return session(user, refreshTokens.issue(user));
    }

    /** L'access token (stateless) expire seul ; on révoque le refresh token fourni. */
    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        refreshTokens.revoke(userId, rawRefreshToken);
        audit.record("LOGOUT", userId, userId, null);
    }

    private AuthSession session(UserAccount user, String rawRefreshToken) {
        return new AuthSession(jwt.generateAccessToken(user), rawRefreshToken, jwt.accessTtlSeconds(), user);
    }
}
''')
    put(d + "PasswordEmailRequested.java", r'''
package com.uasz.alumni.user.service;

import java.time.Duration;

/** Événement : un email de reset (ou d'invitation) doit partir après le commit. Le jeton en clair ne vit que dans l'événement. */
public record PasswordEmailRequested(String email, String firstName, String token, Kind kind, Duration ttl) {

    public enum Kind { RESET, INVITATION }

    @Override
    public String toString() {
        return "PasswordEmailRequested[email=" + email + ", kind=" + kind + ", token=***]";
    }
}
''')
    put(d + "PasswordChangedNotice.java", r'''
package com.uasz.alumni.user.service;

/** Événement : notifier l'utilisateur que son mot de passe vient de changer (alerte en cas de piratage). */
public record PasswordChangedNotice(String email, String firstName) {
}
''')
    put(d + "PasswordEmailListener.java", r'''
package com.uasz.alumni.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie les emails liés au mot de passe après le commit, en asynchrone : une panne SMTP ne fait pas
 * échouer l'opération, et le temps de réponse de /auth/forgot-password ne révèle pas si le compte existe.
 */
@Component
public class PasswordEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordEmailListener.class);

    private final EmailService emailService;

    public PasswordEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordEmailRequested(PasswordEmailRequested event) {
        try {
            switch (event.kind()) {
                case RESET -> emailService.sendPasswordResetEmail(
                        event.email(), event.firstName(), event.token(), event.ttl());
                case INVITATION -> emailService.sendAccountInvitationEmail(
                        event.email(), event.firstName(), event.token(), event.ttl());
            }
        } catch (RuntimeException e) {
            log.error("Échec d'envoi de l'email ({}) à {} : {}", event.kind(), event.email(), e.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChanged(PasswordChangedNotice event) {
        try {
            emailService.sendPasswordChangedNotice(event.email(), event.firstName());
        } catch (RuntimeException e) {
            log.error("Échec d'envoi de l'avis de changement de mot de passe à {} : {}", event.email(), e.getMessage());
        }
    }
}
''')
    put(d + "EmailService.java", r'''
package com.uasz.alumni.user.service;

import java.time.Duration;

/** Envoi des emails transactionnels (abstraction : facilite les tests et un autre fournisseur). */
public interface EmailService {

    void sendConfirmationEmail(String to, String firstName, String token);

    void sendPasswordResetEmail(String to, String firstName, String token, Duration ttl);

    /** Compte créé par un administrateur : le lien permet de définir le mot de passe et active le compte. */
    void sendAccountInvitationEmail(String to, String firstName, String token, Duration ttl);

    void sendPasswordChangedNotice(String to, String firstName);
}
''')

    # SmtpEmailService : on AJOUTE sans toucher à sendConfirmationEmail ni humanize
    sub(d + "SmtpEmailService.java", "    private static String humanize(Duration ttl) {", r'''    /** Route frontend qui lit ?token= et appelle POST /auth/reset-password (à aligner avec votre frontend). */
    private static final String RESET_PATH = "/reinitialisation-mot-de-passe";

    @Override
    public void sendPasswordResetEmail(String to, String firstName, String token, Duration ttl) {
        send(to, "Alumni UASZ : réinitialisation de votre mot de passe", """
                Bonjour %s,

                Une réinitialisation du mot de passe de votre compte Alumni UASZ a été demandée.

                Pour choisir un nouveau mot de passe, ouvrez ce lien :
                %s

                Ce lien est valable %s. Si vous n'êtes pas à l'origine de cette demande, ignorez ce message :
                votre mot de passe reste inchangé.
                """.formatted(firstName, link(RESET_PATH, token), humanize(ttl)));
    }

    @Override
    public void sendAccountInvitationEmail(String to, String firstName, String token, Duration ttl) {
        send(to, "Alumni UASZ : activez votre compte", """
                Bonjour %s,

                Un compte vient d'être créé pour vous sur la plateforme Alumni de l'Université Assane Seck de Ziguinchor.

                Pour activer votre compte et choisir votre mot de passe, ouvrez ce lien :
                %s

                Ce lien est valable %s. Si vous n'attendiez pas cet email, ignorez-le : aucun accès ne sera ouvert.
                """.formatted(firstName, link(RESET_PATH, token), humanize(ttl)));
    }

    @Override
    public void sendPasswordChangedNotice(String to, String firstName) {
        send(to, "Alumni UASZ : votre mot de passe a été modifié", """
                Bonjour %s,

                Le mot de passe de votre compte Alumni UASZ vient d'être modifié.

                Ce n'était pas vous ? Réinitialisez immédiatement votre mot de passe via « Mot de passe oublié »
                et contactez l'administration.
                """.formatted(firstName));
    }

    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path(path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    private static String humanize(Duration ttl) {''', "SmtpEmailService : emails reset / invitation / avis", skip_if="RESET_PATH")

    put(d + "PasswordService.java", r'''
package com.uasz.alumni.user.service;

import com.uasz.alumni.user.config.AppProperties;
import com.uasz.alumni.user.config.AuthProperties;
import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.InvalidCurrentPasswordException;
import com.uasz.alumni.user.exceptions.InvalidResetTokenException;
import com.uasz.alumni.user.exceptions.UserNotFoundException;
import com.uasz.alumni.user.exceptions.WeakPasswordException;
import com.uasz.alumni.user.model.PasswordResetToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.PasswordResetTokenRepository;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.PasswordPolicy;
import com.uasz.alumni.user.utils.TextUtils;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AL-20 : mot de passe oublié, réinitialisation par jeton, changement de mot de passe, invitation d'un compte. */
@Service
public class PasswordService {

    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final AuthProperties authProps;
    private final AppProperties appProps;
    private final AuditService audit;
    private final Clock clock;

    public PasswordService(UserAccountRepository users, PasswordResetTokenRepository resetTokens,
                           RefreshTokenService refreshTokens, PasswordEncoder encoder,
                           ApplicationEventPublisher events, AuthProperties authProps, AppProperties appProps,
                           AuditService audit, Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.events = events;
        this.authProps = authProps;
        this.appProps = appProps;
        this.audit = audit;
        this.clock = clock;
    }

    /** Réponse identique que le compte existe ou non. Comptes INACTIF/SUSPENDU et demandes trop rapprochées : ignorés. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        Instant now = clock.instant();
        users.findByEmail(TextUtils.normalizeEmail(rawEmail)).ifPresent(user -> {
            if (user.getStatus() == UserStatus.INACTIF || user.getStatus() == UserStatus.SUSPENDU) {
                return;
            }
            boolean tooSoon = resetTokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                    .map(t -> t.getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now))
                    .orElse(false);
            if (tooSoon) {
                return;
            }
            issueToken(user, authProps.passwordResetTtl(), PasswordEmailRequested.Kind.RESET);
            audit.record("PASSWORD_RESET_REQUESTED", user.getId(), user.getId(), null);
        });
    }

    /** Compte créé par un administrateur : envoie le lien « définir mon mot de passe » (valable comme la confirmation d'email). */
    @Transactional
    public void sendInvitation(UserAccount user) {
        issueToken(user, appProps.confirmationTtl(), PasswordEmailRequested.Kind.INVITATION);
    }

    /**
     * Consomme le jeton, pose le nouveau mot de passe, active un compte EN_ATTENTE (la réception du lien
     * prouve le contrôle de l'adresse), lève un éventuel verrouillage et ferme toutes les sessions.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHashWithUser(TokenUtils.sha256Hex(rawToken.trim()))
                .orElseThrow(InvalidResetTokenException::new);
        UserAccount user = token.getUser();

        if (token.isUsed() || token.isExpiredAt(now)
                || user.getStatus() == UserStatus.INACTIF || user.getStatus() == UserStatus.SUSPENDU) {
            throw new InvalidResetTokenException();
        }
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new WeakPasswordException(HttpStatus.valueOf(422));
        }

        token.markUsed(now);
        user.changePasswordHash(encoder.encode(newPassword));
        if (user.getStatus() == UserStatus.EN_ATTENTE) {
            user.activate(now);
        }
        user.clearLock();
        refreshTokens.revokeAll(user.getId());
        events.publishEvent(new PasswordChangedNotice(user.getEmail(), user.getFirstName()));
        audit.record("PASSWORD_RESET", user.getId(), user.getId(), null);
    }

    /** Utilisateur connecté. Toutes ses sessions sont fermées : il doit se reconnecter. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new WeakPasswordException(HttpStatus.valueOf(422));
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED",
                    "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        user.changePasswordHash(encoder.encode(newPassword));
        refreshTokens.revokeAll(userId);
        events.publishEvent(new PasswordChangedNotice(user.getEmail(), user.getFirstName()));
        audit.record("PASSWORD_CHANGED", userId, userId, null);
    }

    private void issueToken(UserAccount user, Duration ttl, PasswordEmailRequested.Kind kind) {
        resetTokens.deleteAllByUserId(user.getId());

        Instant now = clock.instant();
        String rawToken = TokenUtils.generateToken();
        resetTokens.save(new PasswordResetToken(user, TokenUtils.sha256Hex(rawToken), now, now.plus(ttl)));

        events.publishEvent(new PasswordEmailRequested(user.getEmail(), user.getFirstName(), rawToken, kind, ttl));
    }
}
''')
    put(d + "UserService.java", r'''
package com.uasz.alumni.user.service;

import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.EmailAlreadyUsedException;
import com.uasz.alumni.user.exceptions.ForbiddenException;
import com.uasz.alumni.user.exceptions.UserNotFoundException;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.CurrentUser;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserCreateRequest;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import com.uasz.alumni.user.utils.TextUtils;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des utilisateurs. Les routes réservées à l'ADMINISTRATEUR sont aussi verrouillées dans SecurityConfig. */
@Service
public class UserService {

    private final UserAccountRepository users;
    private final RefreshTokenService refreshTokens;
    private final PasswordService passwords;
    private final PasswordEncoder encoder;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final Clock clock;

    public UserService(UserAccountRepository users, RefreshTokenService refreshTokens, PasswordService passwords,
                       PasswordEncoder encoder, CurrentUser currentUser, AuditService audit, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.encoder = encoder;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> list(RoleName role, UserStatus status, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(
                page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        if (role != null && status != null) {
            return users.findByRoleAndStatus(role, status, pageable);
        }
        if (role != null) {
            return users.findByRole(role, pageable);
        }
        if (status != null) {
            return users.findByStatus(status, pageable);
        }
        return users.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public UserAccount get(UUID id) {
        currentUser.requireSelfOrAdmin(id);
        return load(id);
    }

    /** Création par un ADMINISTRATEUR : mot de passe aléatoire inconnu de tous, l'utilisateur le définit via le lien reçu. */
    @Transactional
    public UserAccount create(UserCreateRequest request) {
        String email = TextUtils.normalizeEmail(request.getEmail());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        UserAccount account = new UserAccount(
                email,
                encoder.encode(TokenUtils.generateToken()),
                request.getFirstName().trim(),
                request.getLastName().trim(),
                request.getRole(),
                UserStatus.EN_ATTENTE);
        if (request.getPhone() != null) {
            account.updateProfile(null, null, request.getPhone().trim(), null, null);
        }
        try {
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
        passwords.sendInvitation(account);
        audit.record("USER_CREATED", currentUser.require().id(), account.getId(), "role=" + account.getRole());
        return account;
    }

    /** Soi-même ou ADMINISTRATEUR. Mise à jour partielle. L'email n'est modifiable que par un ADMINISTRATEUR. */
    @Transactional
    public UserAccount update(UUID id, UserUpdateRequest r) {
        currentUser.requireSelfOrAdmin(id);
        UserAccount account = load(id);

        if (r.getFirstName() != null && r.getFirstName().isBlank()) {
            throw badRequest("firstName ne peut pas être vide.");
        }
        if (r.getLastName() != null && r.getLastName().isBlank()) {
            throw badRequest("lastName ne peut pas être vide.");
        }
        if (r.getPhone() != null && r.getPhone().trim().length() > 30) {
            throw badRequest("phone : 30 caractères maximum.");
        }
        if (r.getBirthDate() != null && !r.getBirthDate().isBefore(LocalDate.now(clock))) {
            throw badRequest("birthDate doit être dans le passé.");
        }

        if (r.getEmail() != null) {
            String newEmail = TextUtils.normalizeEmail(r.getEmail());
            if (!newEmail.equals(account.getEmail())) {
                if (!currentUser.isAdmin()) {
                    throw new ForbiddenException("Seul un administrateur peut modifier l'adresse email.");
                }
                if (users.existsByEmail(newEmail)) {
                    throw new EmailAlreadyUsedException();
                }
                account.changeEmail(newEmail);
            }
        }

        account.updateProfile(
                r.getFirstName() == null ? null : r.getFirstName().trim(),
                r.getLastName() == null ? null : r.getLastName().trim(),
                r.getPhone() == null ? null : r.getPhone().trim(),
                r.getGender(),
                r.getBirthDate());
        try {
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
        audit.record("USER_UPDATED", currentUser.require().id(), id, null);
        return account;
    }

    @Transactional
    public UserAccount updateStatus(UUID id, UserStatus newStatus) {
        UUID actorId = currentUser.require().id();
        UserAccount account = load(id);

        if (account.getId().equals(actorId)) {
            throw selfModification();
        }
        if (newStatus == null || newStatus == UserStatus.EN_ATTENTE) {
            throw badRequest("Statut invalide : seuls ACTIF, INACTIF et SUSPENDU peuvent être attribués.");
        }
        if (newStatus != account.getStatus()) {
            if (newStatus != UserStatus.ACTIF) {
                guardLastAdmin(account);
            }
            account.changeStatus(newStatus);
            if (newStatus == UserStatus.ACTIF) {
                account.clearLock();
            } else {
                refreshTokens.revokeAll(id);
            }
            audit.record("USER_STATUS_CHANGED", actorId, id, "status=" + newStatus);
        }
        return account;
    }

    @Transactional
    public void delete(UUID id) {
        UUID actorId = currentUser.require().id();
        UserAccount account = load(id);

        if (account.getId().equals(actorId)) {
            throw selfModification();
        }
        guardLastAdmin(account);
        users.delete(account);
        audit.record("USER_DELETED", actorId, id, "email=" + account.getEmail());
    }

    // ───── privé ─────

    private UserAccount load(UUID id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }

    /** Interdit de désactiver, suspendre ou supprimer le dernier ADMINISTRATEUR actif. */
    private void guardLastAdmin(UserAccount target) {
        if (target.getRole() == RoleName.ADMINISTRATEUR && target.getStatus() == UserStatus.ACTIF
                && users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN",
                    "Opération impossible : il doit rester au moins un administrateur actif.");
        }
    }

    private static ApiException selfModification() {
        return new ApiException(HttpStatus.CONFLICT, "CANNOT_MODIFY_SELF",
                "Vous ne pouvez pas effectuer cette action sur votre propre compte.");
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
''')
    put(d + "RoleService.java", r'''
package com.uasz.alumni.user.service;

import com.uasz.alumni.user.spi.model.Role;
import com.uasz.alumni.user.spi.model.RoleName;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Catalogue des rôles. Les rôles sont une énumération du contrat (pas une table) : l'id est un UUID
 * déterministe dérivé du nom, donc stable entre redémarrages et instances.
 * ATTENTION : la matrice de permissions ci-dessous est un point de départ à valider avec l'équipe.
 */
@Service
public class RoleService {

    private static final Map<RoleName, List<String>> PERMISSIONS = new EnumMap<>(RoleName.class);

    static {
        PERMISSIONS.put(RoleName.ETUDIANT, List.of(
                "profile:read", "profile:update", "cv:manage", "offers:read", "applications:create", "messages:send"));
        PERMISSIONS.put(RoleName.ALUMNI, List.of(
                "profile:read", "profile:update", "offers:read", "proposals:create", "messages:send"));
        PERMISSIONS.put(RoleName.ENSEIGNANT, List.of(
                "profile:read", "profile:update", "students:read", "alumni:read", "analytics:read", "messages:send"));
        PERMISSIONS.put(RoleName.ENTREPRISE, List.of(
                "profile:read", "profile:update", "company:manage", "offers:manage", "applications:read", "messages:send"));
        PERMISSIONS.put(RoleName.ADMINISTRATEUR, List.of(
                "users:read", "users:create", "users:update", "users:delete", "users:status", "roles:read",
                "promotions:manage", "companies:manage", "offers:moderate", "analytics:read", "messages:send"));
    }

    public List<Role> listRoles() {
        return Arrays.stream(RoleName.values())
                .map(name -> new Role()
                        .id(UUID.nameUUIDFromBytes(("alumni-uasz:role:" + name.name()).getBytes(StandardCharsets.UTF_8)))
                        .name(name)
                        .permissions(PERMISSIONS.getOrDefault(name, List.of())))
                .toList();
    }
}
''')
    put(d + "TokenCleanupJob.java", r'''
package com.uasz.alumni.user.service;

import com.uasz.alumni.user.repository.PasswordResetTokenRepository;
import com.uasz.alumni.user.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Purge quotidienne des jetons expirés depuis plus d'un jour (suppression idempotente : sans risque avec 2 réplicas). */
@Component
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final Clock clock;

    public TokenCleanupJob(RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        Instant limit = clock.instant().minus(Duration.ofDays(1));
        int refresh = refreshTokens.deleteExpired(limit);
        int reset = resetTokens.deleteExpired(limit);
        log.info("Purge des jetons : {} refresh, {} reset", refresh, reset);
    }
}
''')

    # RegistrationService : 3 retouches (le constructeur ne change pas)
    rs = d + "RegistrationService.java"
    sub(rs, "import com.uasz.alumni.user.exceptions.RoleNotAllowedException;\n",
        "import com.uasz.alumni.user.exceptions.RoleNotAllowedException;\nimport com.uasz.alumni.user.exceptions.WeakPasswordException;\n",
        "import WeakPasswordException", skip_if="WeakPasswordException;")
    sub(rs, "import com.uasz.alumni.user.utils.TokenUtils;\n",
        "import com.uasz.alumni.user.utils.PasswordPolicy;\nimport com.uasz.alumni.user.utils.TokenUtils;\n",
        "import PasswordPolicy", skip_if="utils.PasswordPolicy;")
    sub(rs, "import org.springframework.dao.DataIntegrityViolationException;\n",
        "import org.springframework.dao.DataIntegrityViolationException;\nimport org.springframework.http.HttpStatus;\n",
        "import HttpStatus", skip_if="import org.springframework.http.HttpStatus;")
    sub(rs, "            EnumSet.of(RoleName.ALUMNI, RoleName.ETUDIANT, RoleName.ENSEIGNANT);",
        "            EnumSet.of(RoleName.ALUMNI, RoleName.ETUDIANT, RoleName.ENSEIGNANT, RoleName.ENTREPRISE);",
        "ENTREPRISE auto-inscriptible", skip_if="RoleName.ENSEIGNANT, RoleName.ENTREPRISE")
    sub(rs, "            throw new RoleNotAllowedException();\n        }\n        String email = normalizeEmail(request.getEmail());",
        "            throw new RoleNotAllowedException();\n        }\n"
        "        if (!PasswordPolicy.isValid(request.getPassword())) {\n"
        "            throw new WeakPasswordException(HttpStatus.BAD_REQUEST);\n        }\n"
        "        String email = normalizeEmail(request.getEmail());",
        "politique de mot de passe à l'inscription", skip_if="PasswordPolicy.isValid(request.getPassword())")


def java_controllers():
    c = BASE + "/controller/"
    put(c + "AuthController.java", r'''
package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.security.CurrentUser;
import com.uasz.alumni.user.service.AuthService;
import com.uasz.alumni.user.service.AuthSession;
import com.uasz.alumni.user.service.PasswordService;
import com.uasz.alumni.user.service.RegistrationService;
import com.uasz.alumni.user.spi.api.AuthApi;
import com.uasz.alumni.user.spi.model.AuthResponse;
import com.uasz.alumni.user.spi.model.ChangePasswordRequest;
import com.uasz.alumni.user.spi.model.ConfirmEmailRequest;
import com.uasz.alumni.user.spi.model.ForgotPasswordRequest;
import com.uasz.alumni.user.spi.model.LoginRequest;
import com.uasz.alumni.user.spi.model.LogoutRequest;
import com.uasz.alumni.user.spi.model.MessageResponse;
import com.uasz.alumni.user.spi.model.RefreshTokenRequest;
import com.uasz.alumni.user.spi.model.RegisterRequest;
import com.uasz.alumni.user.spi.model.ResendConfirmationRequest;
import com.uasz.alumni.user.spi.model.ResetPasswordRequest;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.utils.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final RegistrationService registration;
    private final AuthService auth;
    private final PasswordService passwords;
    private final CurrentUser currentUser;

    public AuthController(RegistrationService registration, AuthService auth,
                          PasswordService passwords, CurrentUser currentUser) {
        this.registration = registration;
        this.auth = auth;
        this.passwords = passwords;
        this.currentUser = currentUser;
    }

    // ================= AL-18 : INSCRIPTION =================

    @Override
    public ResponseEntity<User> registerUser(RegisterRequest registerRequest) {
        UserAccount account = registration.register(registerRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserMapper.toApi(account));
    }

    @Override
    public ResponseEntity<MessageResponse> confirmEmail(
            ConfirmEmailRequest confirmEmailRequest) {

        registration.confirmEmail(confirmEmailRequest.getToken());

        return ResponseEntity.ok(
                new MessageResponse().message("Compte activé avec succès.")
        );
    }

    @Override
    public ResponseEntity<MessageResponse> resendConfirmation(
            ResendConfirmationRequest resendConfirmationRequest) {

        registration.resendConfirmation(resendConfirmationRequest.getEmail());

        return ResponseEntity.ok(
                new MessageResponse().message(
                        "Si un compte en attente existe pour cette adresse, "
                        + "un email de confirmation vient d'être envoyé."
                )
        );
    }

    // ================= AL-19 : AUTHENTIFICATION =================

    @Override
    public ResponseEntity<AuthResponse> loginUser(LoginRequest loginRequest) {
        return ResponseEntity.ok(toResponse(auth.login(loginRequest.getEmail(), loginRequest.getPassword())));
    }

    @Override
    public ResponseEntity<AuthResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        return ResponseEntity.ok(toResponse(auth.refresh(refreshTokenRequest.getRefreshToken())));
    }

    @Override
    public ResponseEntity<Void> logoutUser(LogoutRequest logoutRequest) {
        auth.logout(currentUser.require().id(), logoutRequest.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    // ================= AL-20 : MOT DE PASSE =================

    @Override
    public ResponseEntity<MessageResponse> forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
        passwords.forgotPassword(forgotPasswordRequest.getEmail());

        return ResponseEntity.ok(new MessageResponse().message(
                "Si un compte correspond à cette adresse, un email de réinitialisation vient d'être envoyé."));
    }

    @Override
    public ResponseEntity<MessageResponse> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        passwords.resetPassword(resetPasswordRequest.getToken(), resetPasswordRequest.getNewPassword());

        return ResponseEntity.ok(new MessageResponse().message("Mot de passe réinitialisé avec succès."));
    }

    @Override
    public ResponseEntity<MessageResponse> changePassword(ChangePasswordRequest changePasswordRequest) {
        passwords.changePassword(currentUser.require().id(),
                changePasswordRequest.getCurrentPassword(), changePasswordRequest.getNewPassword());

        return ResponseEntity.ok(new MessageResponse().message(
                "Mot de passe modifié. Veuillez vous reconnecter."));
    }

    private static AuthResponse toResponse(AuthSession session) {
        return new AuthResponse()
                .accessToken(session.accessToken())
                .refreshToken(session.refreshToken())
                .expiresIn((int) session.expiresInSeconds())
                .user(UserMapper.toApi(session.user()));
    }
}
''')
    put(c + "UsersController.java", r'''
package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.service.UserService;
import com.uasz.alumni.user.spi.api.UsersApi;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserCreateRequest;
import com.uasz.alumni.user.spi.model.UserPagedResponse;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserStatusUpdateRequest;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import com.uasz.alumni.user.utils.UserMapper;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController implements UsersApi {

    private final UserService users;

    public UsersController(UserService users) {
        this.users = users;
    }

    @Override
    public ResponseEntity<UserPagedResponse> listUsers(RoleName role, UserStatus status, Integer page, Integer size) {
        Page<UserAccount> result = users.list(role, status, page, size);
        return ResponseEntity.ok(new UserPagedResponse()
                .content(result.getContent().stream().map(UserMapper::toApi).toList())
                .totalElements((int) result.getTotalElements())
                .page(result.getNumber())
                .size(result.getSize()));
    }

    @Override
    public ResponseEntity<User> createUser(UserCreateRequest userCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserMapper.toApi(users.create(userCreateRequest)));
    }

    @Override
    public ResponseEntity<User> getUserById(UUID id) {
        return ResponseEntity.ok(UserMapper.toApi(users.get(id)));
    }

    @Override
    public ResponseEntity<User> updateUser(UUID id, UserUpdateRequest userUpdateRequest) {
        return ResponseEntity.ok(UserMapper.toApi(users.update(id, userUpdateRequest)));
    }

    @Override
    public ResponseEntity<Void> deleteUser(UUID id) {
        users.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<User> updateUserStatus(UUID id, UserStatusUpdateRequest userStatusUpdateRequest) {
        return ResponseEntity.ok(UserMapper.toApi(users.updateStatus(id, userStatusUpdateRequest.getStatus())));
    }
}
''')
    put(c + "RolesController.java", r'''
package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.service.RoleService;
import com.uasz.alumni.user.spi.api.RolesApi;
import com.uasz.alumni.user.spi.model.Role;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RolesController implements RolesApi {

    private final RoleService roles;

    public RolesController(RoleService roles) {
        this.roles = roles;
    }

    @Override
    public ResponseEntity<List<Role>> listRoles() {
        return ResponseEntity.ok(roles.listRoles());
    }
}
''')


# ----------------------------------------------------------------------------
# Programme principal
# ----------------------------------------------------------------------------
def run(cmd, cwd):
    print(f"  $ {' '.join(cmd)}   (dans {cwd})")
    subprocess.run(cmd, cwd=cwd, check=True)


def main():
    global ROOT
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default=".", help="racine du monorepo (défaut : dossier courant)")
    ap.add_argument("--dry-run", action="store_true", help="simule sans rien écrire")
    ap.add_argument("--skip-spi", action="store_true", help="ne lance pas Maven (bump + régénération du SPI)")
    args = ap.parse_args()
    ROOT = Path(args.root).resolve()

    for must in ("user-service/pom.xml", "contracts/user-service.yaml", "spis/build.sh"):
        if not (ROOT / must).exists():
            sys.exit(f"Erreur : {must} introuvable. Lancez le script depuis la racine du monorepo (ou --root).")

    print(f"Racine : {ROOT}\n")
    contract()
    config_and_infra()
    java_exceptions()
    java_config()
    java_model_repo()
    java_utils_security()
    java_services()
    java_controllers()

    print("\n".join(LOG))
    if ERRORS:
        print("\nERREURS : aucune modification écrite.")
        for e in ERRORS:
            print("  x " + e)
        print("\nLes fichiers ont probablement divergé de ce que le script attend : "
              "envoyez-moi le message ci-dessus et le fichier concerné.")
        sys.exit(1)

    changed = {p: c for p, c in FILES.items() if ORIGINAL.get(p) != c}
    print(f"\n{len(changed)} fichier(s) à écrire.")
    if args.dry_run:
        print("--dry-run : rien n'a été écrit.")
        return

    for rel, content in changed.items():
        p = ROOT / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8", newline="\n")
    sh = ROOT / "user-service/scripts/gen-env.sh"
    if sh.exists():
        sh.chmod(sh.stat().st_mode | 0o111)

    spi_done = (ROOT / "spis/repo/com/uasz/alumni/user-service-spi/1.1.0").exists()
    if args.skip_spi:
        print("\n--skip-spi : à faire à la main AVANT de compiler user-service :")
        print("  cd spis && ./mvnw -B -q versions:set -DnewVersion=1.1.0 -DgenerateBackupPoms=false && cd ..")
        print("  ./spis/build.sh user-service")
    elif spi_done:
        print("\nSPI 1.1.0 déjà publié dans spis/repo : étape ignorée.")
    else:
        print("\nBump des SPIs en 1.1.0 puis génération du SPI user-service...")
        run(["./mvnw", "-B", "-q", "versions:set", "-DnewVersion=1.1.0", "-DgenerateBackupPoms=false"], ROOT / "spis")
        run(["./spis/build.sh", "user-service"], ROOT)

    print("""
Terminé. Suite :
  1. git diff --stat                      # relire les changements
  2. cd user-service && make env-complete # ajoute les nouvelles clés + génère BOOTSTRAP_ADMIN_PASSWORD
  3. ./mvnw test && make run              # Flyway applique V3, l'admin initial est créé
  4. git add contracts spis user-service  # puis commit / PR
Note : les autres services gardent leur SPI en 1.0.0 (leurs contrats n'ont pas changé).""")


if __name__ == "__main__":
    main()
