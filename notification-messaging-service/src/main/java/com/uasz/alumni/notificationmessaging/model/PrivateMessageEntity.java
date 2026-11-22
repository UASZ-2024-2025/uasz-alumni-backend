package com.uasz.alumni.notificationmessaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    /** {@code null} une fois le message supprimé. */
    @Column(length = 5000)
    private String content;

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

    /** Suppression pour les deux interlocuteurs : le message reste dans le fil, sans contenu. */
    public void delete() {
        this.deleted = true;
        this.content = null;
    }

    public void edit(String newContent, Instant now) {
        this.content = newContent;
        this.editedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getSenderId() { return senderId; }
    public UUID getRecipientId() { return recipientId; }
    public String getContent() { return content; }
    public Instant getSentAt() { return sentAt; }
    public Instant getReadAt() { return readAt; }
    public boolean isDeleted() { return deleted; }
    public Instant getEditedAt() { return editedAt; }
}
