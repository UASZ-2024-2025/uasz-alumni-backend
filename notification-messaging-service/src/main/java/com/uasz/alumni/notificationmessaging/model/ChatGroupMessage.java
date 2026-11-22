package com.uasz.alumni.notificationmessaging.model;

import com.uasz.alumni.notificationmessaging.spi.model.GroupMessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Message d'un groupe (table chat_group_messages) : texte d'un membre ou événement SYSTEM. */
@Entity
@Table(name = "chat_group_messages")
public class ChatGroupMessage {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupMessageType type;

    @Column(length = 4000)
    private String content;

    @Column(nullable = false)
    private boolean deleted;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime editedAt;

    /** Généré par la base : ordre d'insertion, pour départager deux messages de même horodatage. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    protected ChatGroupMessage() {
    }

    public ChatGroupMessage(UUID groupId, UUID senderId, GroupMessageType type, String content, OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.createdAt = now;
    }

    /** Suppression « pour tous » : le message reste dans le fil, sans contenu. */
    public void delete() {
        this.deleted = true;
        this.content = null;
    }

    public void edit(String newContent, OffsetDateTime now) {
        this.content = newContent;
        this.editedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public UUID getSenderId() { return senderId; }
    public GroupMessageType getType() { return type; }
    public String getContent() { return content; }
    public boolean isDeleted() { return deleted; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getEditedAt() { return editedAt; }
}
