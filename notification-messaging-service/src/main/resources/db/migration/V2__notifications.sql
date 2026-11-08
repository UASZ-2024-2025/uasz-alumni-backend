-- Notifications (in-app, email, push). user_id référence un compte de user-service : simple UUID.
CREATE TABLE notifications (
    id           UUID                     PRIMARY KEY,
    user_id      UUID                     NOT NULL,
    type         VARCHAR(40)              NOT NULL,
    channel      VARCHAR(20)              NOT NULL,
    title        VARCHAR(200)             NOT NULL,
    content      TEXT                     NOT NULL,
    reference_id VARCHAR(100),
    is_read      BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Service (ou administrateur) qui a émis la notification, pour l'audit
    emitted_by   VARCHAR(100)             NOT NULL
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read);
