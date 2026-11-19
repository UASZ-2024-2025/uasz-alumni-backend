-- ALUM-88 : promotions académiques (création par un administrateur).
-- V2, V3, V5 et V9 sont prises par des branches en cours (recommandations, entreprises, profil étudiant).
-- Les diplômés d'une promotion sont les Alumni de même année de diplôme et de même spécialité :
-- ils ne sont pas stockés ici, graduateCount est calculé.
CREATE TABLE promotions (
    id            UUID                     PRIMARY KEY,
    academic_year INTEGER                  NOT NULL,
    specialty     VARCHAR(150)             NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_promotions_academic_year CHECK (academic_year BETWEEN 1990 AND 2100)
);

-- Une seule promotion par année et spécialité, sans tenir compte de la casse.
CREATE UNIQUE INDEX uq_promotions_year_specialty ON promotions (academic_year, LOWER(specialty));

-- Recherche des diplômés d'une promotion.
CREATE INDEX idx_alumni_profiles_promotion ON alumni_profiles (graduation_year, LOWER(specialty));
