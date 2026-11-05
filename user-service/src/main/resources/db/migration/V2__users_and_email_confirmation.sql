-- AL-18 : inscription et confirmation de l'adresse email.

CREATE TABLE users (
    id                UUID                     PRIMARY KEY,
    email             VARCHAR(255)             NOT NULL,
    password_hash     VARCHAR(100)             NOT NULL,
    first_name        VARCHAR(100)             NOT NULL,
    last_name         VARCHAR(100)             NOT NULL,
    role              VARCHAR(20)              NOT NULL,
    status            VARCHAR(20)              NOT NULL,
    email_verified_at TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role   CHECK (role   IN ('ALUMNI', 'ETUDIANT', 'ENSEIGNANT', 'ADMINISTRATEUR')),
    CONSTRAINT ck_users_status CHECK (status IN ('EN_ATTENTE', 'ACTIF', 'INACTIF', 'SUSPENDU'))
);

-- Le jeton en clair n'est jamais stocké : seule son empreinte SHA-256 (64 caractères hexadécimaux) l'est.
CREATE TABLE email_confirmation_tokens (
    id         UUID                     PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)                 NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_email_confirmation_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_confirmation_tokens_user ON email_confirmation_tokens (user_id);
