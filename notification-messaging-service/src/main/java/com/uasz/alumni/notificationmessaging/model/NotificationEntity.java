package com.uasz.alumni.notificationmessaging.model;

import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Notification adressée à un utilisateur (table notifications, ALUM-135) ; lue dans son centre (ALUM-139). */
@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    private String referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime readAt;

    protected NotificationEntity() {
    }

    public NotificationEntity(UUID userId, NotificationType type, NotificationChannel channel, String title,
                              String content, String referenceId, OffsetDateTime createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.channel = channel;
        this.title = title;
        this.content = content;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }

    /** Sans effet si elle est déjà lue : la date de première lecture est conservée. */
    public void markRead(OffsetDateTime now) {
        if (!read) {
            read = true;
            readAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getReferenceId() { return referenceId; }
    public boolean isRead() { return read; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getReadAt() { return readAt; }
}
