-- AL-27 : recommandation d'un étudiant par un Alumni auprès d'une entreprise.
-- Pas de clé étrangère : étudiants, Alumni et entreprises sont gérés par d'autres migrations/tickets.
CREATE TABLE recommendations (
    id             UUID         PRIMARY KEY,
    recommender_id UUID         NOT NULL,
    student_id     UUID         NOT NULL,
    company_id     UUID         NOT NULL,
    comments       TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_recommendations_student ON recommendations (student_id);
