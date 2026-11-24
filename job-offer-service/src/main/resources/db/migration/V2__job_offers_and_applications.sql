-- Offres et candidatures (AL-33 : traitement des candidatures par le recruteur).
-- Les colonnes suivent les schémas JobOffer / Application du contrat job-offer-service.yaml,
-- afin d'être réutilisées par les tickets de publication d'offre et de dépôt de candidature.

CREATE TABLE job_offers (
    id              UUID          PRIMARY KEY,
    title           VARCHAR(255)  NOT NULL,
    type            VARCHAR(20)   NOT NULL CHECK (type IN ('INTERNSHIP', 'JOB')),
    description     TEXT          NOT NULL,
    company_id      UUID          NOT NULL,
    company_name    VARCHAR(255),
    publisher_id    UUID          NOT NULL,   -- auteur de l'offre (claim "sub" du JWT)
    publisher_name  VARCHAR(255),
    location        VARCHAR(255)  NOT NULL,
    remote_type     VARCHAR(20)   CHECK (remote_type IN ('ON_SITE', 'HYBRID', 'REMOTE')),
    duration_months INTEGER,
    salary_range    VARCHAR(100),
    deadline        DATE,
    status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED', 'ARCHIVED')),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_job_offers_publisher ON job_offers (publisher_id);

CREATE TABLE job_offer_required_skills (
    offer_id UUID         NOT NULL REFERENCES job_offers (id) ON DELETE CASCADE,
    skill    VARCHAR(100) NOT NULL,
    PRIMARY KEY (offer_id, skill)
);

CREATE TABLE applications (
    id           UUID          PRIMARY KEY,
    offer_id     UUID          NOT NULL REFERENCES job_offers (id) ON DELETE CASCADE,
    student_id   UUID          NOT NULL,   -- identifiant du compte étudiant (destinataire des notifications)
    student_name VARCHAR(255),
    cv_id        UUID,
    cover_letter TEXT,
    status       VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED'
                 CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'REJECTED', 'ACCEPTED')),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Un étudiant ne postule qu'une fois par offre (409 du contrat submitApplication)
    CONSTRAINT uq_applications_offer_student UNIQUE (offer_id, student_id)
);

CREATE INDEX idx_applications_student ON applications (student_id);
