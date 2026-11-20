-- ALUM-168 : rattachement d'un compte ENTREPRISE à sa fiche entreprise.
-- La fiche vit dans alumni-student-company-service : référence par UUID, sans clé étrangère (ADR-0004).
ALTER TABLE users ADD COLUMN company_id UUID;

ALTER TABLE users ADD CONSTRAINT ck_users_company_role
    CHECK (company_id IS NULL OR role = 'ENTREPRISE');

CREATE INDEX idx_users_company_id ON users (company_id) WHERE company_id IS NOT NULL;
