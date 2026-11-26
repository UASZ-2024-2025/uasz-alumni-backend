-- ALUM-148 : index pour la recherche et la fermeture automatique des offres.
CREATE INDEX idx_job_offers_company         ON job_offers (company_id);
CREATE INDEX idx_job_offers_status_deadline ON job_offers (status, deadline);
