package com.uasz.alumni.notificationmessaging.model;

import com.uasz.alumni.notificationmessaging.spi.model.GroupMessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "private_message")
public class PrivateMessageEntity {

    @Id
    private UUID id;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    /** TEXT, STICKER ou ATTACHMENT (jamais SYSTEM en privé). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupMessageType type = GroupMessageType.TEXT;

    /** Texte, ou légende d'une pièce jointe ; {@code null} une fois le message supprimé. */
    @Column(length = 5000)
    private String content;

    @Column(length = 40)
    private String sticker;

    @Embedded
    private StoredAttachment attachment;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "edited_at")
    private Instant editedAt;

    protected PrivateMessageEntity() {}

    public PrivateMessageEntity(UUID senderId, UUID recipientId, String content, Instant sentAt) {
        this.id = UUID.randomUUID();
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.content = content;
        this.sentAt = sentAt;
    }

    public PrivateMessageEntity(UUID senderId, UUID recipientId, GroupMessageType type, String content, String sticker,
                                StoredAttachment attachment, Instant sentAt) {
        this(senderId, recipientId, content, sentAt);
        this.type = type;
        this.sticker = sticker;
        this.attachment = attachment;
    }

    /** Suppression pour les deux interlocuteurs : le message reste dans le fil, sans contenu ; renvoie la pièce jointe à effacer. */
    public StoredAttachment delete() {
        StoredAttachment removed = attachment;
        this.deleted = true;
        this.content = null;
        this.sticker = null;
        this.attachment = null;
        return removed;
    }

    public void edit(String newContent, Instant now) {
        this.content = newContent;
        this.editedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getSenderId() { return senderId; }
    public UUID getRecipientId() { return recipientId; }
    public GroupMessageType getType() { return type; }
    public String getContent() { return content; }
    public String getSticker() { return sticker; }
    public StoredAttachment getAttachment() { return attachment; }
    public Instant getSentAt() { return sentAt; }
    public Instant getReadAt() { return readAt; }
    public boolean isDeleted() { return deleted; }
    public Instant getEditedAt() { return editedAt; }
}
