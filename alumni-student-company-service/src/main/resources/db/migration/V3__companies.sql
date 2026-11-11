-- AL-28 : fiches entreprises (création par un Alumni ou une entreprise, validation partenaire par l'admin).
CREATE TABLE company (
    id            UUID                     PRIMARY KEY,
    owner_id      UUID                     NOT NULL,
    name          VARCHAR(255)             NOT NULL,
    sector        VARCHAR(255)             NOT NULL,
    description   VARCHAR(2000),
    website       VARCHAR(255),
    address       VARCHAR(255),
    contact_email VARCHAR(255)             NOT NULL,
    contact_phone VARCHAR(50),
    logo_url      VARCHAR(500),
    is_partner    BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_company_owner_id   ON company (owner_id);
CREATE INDEX idx_company_is_partner ON company (is_partner);
