-- Groupes de discussion : groupes, membres (rôle, lecture, préférences personnelles) et messages.

-- Annuaire local : nom affiché et rôle applicatif, recopiés du jeton (claims "name" et "role")
-- à chaque appel de l'utilisateur, pour ne pas interroger user-service.
CREATE TABLE chat_users (
    user_id      UUID          PRIMARY KEY,           -- claim "sub" du JWT
    display_name VARCHAR(255),
    app_role     VARCHAR(30),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_groups (
    id                          UUID          PRIMARY KEY,
    name                        VARCHAR(100)  NOT NULL,
    description                 TEXT,
    owner_id                    UUID          NOT NULL,
    only_admins_can_send        BOOLEAN       NOT NULL DEFAULT FALSE,
    only_admins_can_edit_info   BOOLEAN       NOT NULL DEFAULT TRUE,
    only_admins_can_add_members BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at            TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_chat_groups_name CHECK (char_length(trim(name)) BETWEEN 3 AND 100)
);

CREATE TABLE chat_group_members (
    id            UUID          PRIMARY KEY,
    group_id      UUID          NOT NULL REFERENCES chat_groups (id) ON DELETE CASCADE,
    user_id       UUID          NOT NULL,
    group_role    VARCHAR(10)   NOT NULL,
    joined_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_read_at  TIMESTAMPTZ,
    -- Préférences personnelles (invisibles des autres membres)
    pinned        BOOLEAN       NOT NULL DEFAULT FALSE,
    archived      BOOLEAN       NOT NULL DEFAULT FALSE,
    muted_until   TIMESTAMPTZ,
    muted_forever BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_chat_group_members UNIQUE (group_id, user_id),
    CONSTRAINT chk_chat_group_members_role CHECK (group_role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX idx_chat_group_members_user ON chat_group_members (user_id);
-- Règle métier garantie par la base : un seul propriétaire par groupe.
CREATE UNIQUE INDEX uq_chat_group_members_one_owner ON chat_group_members (group_id) WHERE group_role = 'OWNER';

CREATE TABLE chat_group_messages (
    id         UUID           PRIMARY KEY,
    group_id   UUID           NOT NULL REFERENCES chat_groups (id) ON DELETE CASCADE,
    sender_id  UUID           NOT NULL,             -- auteur, ou acteur de l'événement pour un message SYSTEM
    type       VARCHAR(10)    NOT NULL,
    content    VARCHAR(4000),                       -- NULL une fois le message supprimé
    deleted    BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Départage les messages de même horodatage (plusieurs événements dans une même transaction).
    seq        BIGINT         GENERATED ALWAYS AS IDENTITY,
    CONSTRAINT chk_chat_group_messages_type CHECK (type IN ('TEXT', 'SYSTEM')),
    CONSTRAINT chk_chat_group_messages_content CHECK (deleted OR content IS NOT NULL)
);

CREATE INDEX idx_chat_group_messages_group ON chat_group_messages (group_id, created_at DESC, seq DESC);
