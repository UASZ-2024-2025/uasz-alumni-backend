CREATE TABLE private_message (
    id           UUID          PRIMARY KEY,
    sender_id    UUID          NOT NULL,
    recipient_id UUID          NOT NULL,
    content      VARCHAR(5000) NOT NULL,
    sent_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_private_message_not_self CHECK (sender_id <> recipient_id)
);

CREATE INDEX idx_pm_sender_recipient ON private_message (sender_id, recipient_id, sent_at DESC);
CREATE INDEX idx_pm_recipient_unread ON private_message (recipient_id, sender_id) WHERE read_at IS NULL;
