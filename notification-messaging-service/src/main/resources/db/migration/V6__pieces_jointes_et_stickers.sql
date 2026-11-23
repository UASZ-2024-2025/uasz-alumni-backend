-- ALUM-261 : stickers et pièces jointes (image, document, message vocal) dans les groupes et la messagerie privée.
-- Le fichier est chez Cloudinary ; la base garde son identifiant et ce qu'il faut pour l'afficher.

ALTER TABLE chat_group_messages
    DROP CONSTRAINT chk_chat_group_messages_type,
    DROP CONSTRAINT chk_chat_group_messages_content,
    ADD COLUMN sticker                     VARCHAR(40),
    ADD COLUMN attachment_kind             VARCHAR(10),
    ADD COLUMN attachment_public_id        VARCHAR(255),
    ADD COLUMN attachment_version          BIGINT,
    ADD COLUMN attachment_format           VARCHAR(20),
    ADD COLUMN attachment_file_name        VARCHAR(255),
    ADD COLUMN attachment_size_bytes       BIGINT,
    ADD COLUMN attachment_duration_seconds DOUBLE PRECISION,
    ADD COLUMN attachment_width            INTEGER,
    ADD COLUMN attachment_height           INTEGER,
    ADD CONSTRAINT chk_chat_group_messages_type CHECK (type IN ('TEXT', 'SYSTEM', 'STICKER', 'ATTACHMENT')),
    ADD CONSTRAINT chk_chat_group_messages_content CHECK (
        deleted
        OR (type IN ('TEXT', 'SYSTEM') AND content IS NOT NULL)
        OR (type = 'STICKER' AND sticker IS NOT NULL)
        OR (type = 'ATTACHMENT' AND attachment_public_id IS NOT NULL)),
    ADD CONSTRAINT chk_chat_group_messages_attachment_kind CHECK (attachment_kind IN ('IMAGE', 'FILE', 'AUDIO'));

ALTER TABLE private_message
    DROP CONSTRAINT chk_private_message_content,
    ADD COLUMN type                        VARCHAR(10) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN sticker                     VARCHAR(40),
    ADD COLUMN attachment_kind             VARCHAR(10),
    ADD COLUMN attachment_public_id        VARCHAR(255),
    ADD COLUMN attachment_version          BIGINT,
    ADD COLUMN attachment_format           VARCHAR(20),
    ADD COLUMN attachment_file_name        VARCHAR(255),
    ADD COLUMN attachment_size_bytes       BIGINT,
    ADD COLUMN attachment_duration_seconds DOUBLE PRECISION,
    ADD COLUMN attachment_width            INTEGER,
    ADD COLUMN attachment_height           INTEGER,
    ADD CONSTRAINT chk_private_message_type CHECK (type IN ('TEXT', 'STICKER', 'ATTACHMENT')),
    ADD CONSTRAINT chk_private_message_content CHECK (
        deleted
        OR (type = 'TEXT' AND content IS NOT NULL)
        OR (type = 'STICKER' AND sticker IS NOT NULL)
        OR (type = 'ATTACHMENT' AND attachment_public_id IS NOT NULL)),
    ADD CONSTRAINT chk_private_message_attachment_kind CHECK (attachment_kind IN ('IMAGE', 'FILE', 'AUDIO'));

-- Un fichier n'appartient qu'à un message : sinon supprimer l'un effacerait le fichier de l'autre.
-- (Un message supprimé perd son attachment_public_id, l'index ne le compte plus.)
CREATE UNIQUE INDEX uq_chat_group_messages_attachment ON chat_group_messages (attachment_public_id)
    WHERE attachment_public_id IS NOT NULL;
CREATE UNIQUE INDEX uq_private_message_attachment ON private_message (attachment_public_id)
    WHERE attachment_public_id IS NOT NULL;
