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
