-- Parcours professionnel des Alumni (expériences successives).
-- V2 est réservée à la table recommendations (AL-27) : ne pas réutiliser ce numéro.

-- Profil Alumni : current_company / current_position sont alimentés par l'expérience courante.
CREATE TABLE alumni_profiles (
    id               UUID          PRIMARY KEY,
    user_id          UUID          NOT NULL UNIQUE,   -- identifiant du compte (claim "sub" du JWT)
    first_name       VARCHAR(100),
    last_name        VARCHAR(100),
    email            VARCHAR(255),
    graduation_year  INTEGER,
    specialty        VARCHAR(150),
    current_company  VARCHAR(255),
    current_position VARCHAR(255),
    linkedin_url     VARCHAR(500),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE work_experiences (
    id           UUID          PRIMARY KEY,
    alumni_id    UUID          NOT NULL REFERENCES alumni_profiles (id) ON DELETE CASCADE,
    company_name VARCHAR(255)  NOT NULL,
    position     VARCHAR(255)  NOT NULL,
    start_date   DATE          NOT NULL,
    end_date     DATE,
    is_current   BOOLEAN       NOT NULL DEFAULT FALSE,
    description  TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_work_experiences_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_work_experiences_current_no_end CHECK (NOT is_current OR end_date IS NULL)
);

CREATE INDEX idx_work_experiences_alumni ON work_experiences (alumni_id, start_date DESC);

-- Règle métier garantie par la base : au plus UNE expérience courante par Alumni.
CREATE UNIQUE INDEX uq_work_experiences_one_current ON work_experiences (alumni_id) WHERE is_current;
