-- Offres d'emploi / de stage et candidatures.
-- companyId, publisherId, studentId, cvId, student_user_id référencent d'autres services : simples UUID, sans clé étrangère.

CREATE TABLE job_offers (
    id              UUID                     PRIMARY KEY,
    title           VARCHAR(200)             NOT NULL,
    type            VARCHAR(20)              NOT NULL,
    description     TEXT                     NOT NULL,
    company_id      UUID                     NOT NULL,
    company_name    VARCHAR(200),
    publisher_id    UUID                     NOT NULL,
    publisher_name  VARCHAR(200),
    location        VARCHAR(200),
    remote_type     VARCHAR(20),
    duration_months INTEGER,
    salary_range    VARCHAR(100),
    deadline        DATE,
    status          VARCHAR(20)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_job_offers_status ON job_offers (status);
CREATE INDEX idx_job_offers_publisher ON job_offers (publisher_id);

CREATE TABLE job_offer_required_skills (
    offer_id UUID         NOT NULL REFERENCES job_offers (id) ON DELETE CASCADE,
    position INTEGER      NOT NULL,
    skill    VARCHAR(100) NOT NULL,
    PRIMARY KEY (offer_id, position)
);

CREATE TABLE applications (
    id              UUID                     PRIMARY KEY,
    offer_id        UUID                     NOT NULL REFERENCES job_offers (id) ON DELETE CASCADE,
    student_id      UUID                     NOT NULL,
    -- Compte (user-service) de l'étudiant qui a postulé : destinataire des notifications
    student_user_id UUID                     NOT NULL,
    student_name    VARCHAR(200),
    cv_id           UUID,
    cover_letter    TEXT,
    status          VARCHAR(20)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_applications_offer_student UNIQUE (offer_id, student_user_id)
);

CREATE INDEX idx_applications_student_user ON applications (student_user_id);
