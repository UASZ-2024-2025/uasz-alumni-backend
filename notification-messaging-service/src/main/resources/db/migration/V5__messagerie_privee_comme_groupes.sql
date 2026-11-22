-- Messagerie privée alignée sur les groupes : suppression « pour les deux », modification d'un message,
-- préférences personnelles par conversation (épingler, archiver, sourdine).

-- Un message supprimé reste dans le fil, sans contenu (comme dans les groupes).
ALTER TABLE private_message ALTER COLUMN content DROP NOT NULL;
ALTER TABLE private_message ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE private_message ADD COLUMN edited_at TIMESTAMPTZ;
ALTER TABLE private_message ADD CONSTRAINT chk_private_message_content CHECK (deleted OR content IS NOT NULL);

ALTER TABLE chat_group_messages ADD COLUMN edited_at TIMESTAMPTZ;

-- Préférences de user_id sur sa conversation avec other_id (invisibles de other_id).
CREATE TABLE private_conversation_preferences (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL,
    other_id      UUID          NOT NULL,
    pinned        BOOLEAN       NOT NULL DEFAULT FALSE,
    archived      BOOLEAN       NOT NULL DEFAULT FALSE,
    muted_until   TIMESTAMPTZ,
    muted_forever BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_private_conversation_preferences UNIQUE (user_id, other_id),
    CONSTRAINT chk_private_conversation_preferences_not_self CHECK (user_id <> other_id)
);
