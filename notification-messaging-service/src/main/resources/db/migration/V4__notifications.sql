-- ALUM-135 / ALUM-139 : notifications de l'utilisateur (centre de notifications) et e-mail des destinataires.

CREATE TABLE notifications (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL,              -- destinataire (claim "sub" de son JWT)
    type         VARCHAR(40)   NOT NULL,
    channel      VARCHAR(10)   NOT NULL,
    title        VARCHAR(255)  NOT NULL,
    content      VARCHAR(4000) NOT NULL,
    reference_id VARCHAR(255),
    is_read      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at      TIMESTAMPTZ
);

-- Liste paginée (plus récentes d'abord) et compteur de non-lues d'un utilisateur
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread  ON notifications (user_id) WHERE is_read = FALSE;

-- Adresse e-mail recopiée du jeton (comme le nom) : destinataire du canal EMAIL, sans appeler user-service.
ALTER TABLE chat_users ADD COLUMN email VARCHAR(255);
