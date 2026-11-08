package com.uasz.alumni.notificationmessaging.model;

import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "emitted_by", nullable = false, length = 100)
    private String emittedBy;

    protected NotificationEntity() {
    }

    public NotificationEntity(UUID userId, NotificationType type, NotificationChannel channel, String title,
                              String content, String referenceId, String emittedBy) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.channel = channel;
        this.title = title;
        this.content = content;
        this.referenceId = referenceId;
        this.emittedBy = emittedBy;
        this.createdAt = Instant.now();
    }

    public void markAsRead() {
        this.read = true;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getReferenceId() { return referenceId; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
    public String getEmittedBy() { return emittedBy; }
}
