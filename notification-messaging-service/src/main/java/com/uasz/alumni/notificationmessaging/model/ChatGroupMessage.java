package com.uasz.alumni.notificationmessaging.model;

import com.uasz.alumni.notificationmessaging.spi.model.GroupMessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Message d'un groupe (table chat_group_messages) : texte, sticker ou pièce jointe d'un membre, ou événement SYSTEM. */
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

    /** Texte du message, ou légende d'une pièce jointe. */
    @Column(length = 4000)
    private String content;

    @Column(length = 40)
    private String sticker;

    @Embedded
    private StoredAttachment attachment;

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

    public ChatGroupMessage(UUID groupId, UUID senderId, GroupMessageType type, String content, String sticker,
                            StoredAttachment attachment, OffsetDateTime now) {
        this(groupId, senderId, type, content, now);
        this.sticker = sticker;
        this.attachment = attachment;
    }

    /** Suppression « pour tous » : le message reste dans le fil, sans contenu ; renvoie la pièce jointe à effacer. */
    public StoredAttachment delete() {
        StoredAttachment removed = attachment;
        this.deleted = true;
        this.content = null;
        this.sticker = null;
        this.attachment = null;
        return removed;
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
    public String getSticker() { return sticker; }
    public StoredAttachment getAttachment() { return attachment; }
    public boolean isDeleted() { return deleted; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getEditedAt() { return editedAt; }
}
