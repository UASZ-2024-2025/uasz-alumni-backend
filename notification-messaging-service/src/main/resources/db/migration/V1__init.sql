-- Migration initiale du service notification-messaging-service.
-- Les tables métier seront ajoutées par des migrations V2__..., V3__... (ne jamais modifier une migration appliquée).
CREATE TABLE service_info (
    id         INTEGER      PRIMARY KEY,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
