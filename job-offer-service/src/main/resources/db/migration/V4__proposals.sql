-- Propositions directes d'offres aux étudiants (ALUM-174).
CREATE TABLE proposals (
    id             UUID         PRIMARY KEY,
    offer_id       UUID         NOT NULL REFERENCES job_offers (id) ON DELETE CASCADE,
    student_id     UUID         NOT NULL,
    student_name   VARCHAR(255),
    recruiter_id   UUID         NOT NULL,
    recruiter_name VARCHAR(255),
    message        TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    expires_at     TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_proposals_student ON proposals (student_id);
CREATE INDEX idx_proposals_recruiter ON proposals (recruiter_id);
CREATE INDEX idx_proposals_status_expires_at ON proposals (status, expires_at);
CREATE UNIQUE INDEX uq_proposals_pending_offer_student
    ON proposals (offer_id, student_id) WHERE status = 'PENDING';
