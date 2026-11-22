package com.uasz.alumni.notificationmessaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Préférences de {@code userId} sur sa conversation avec {@code otherId} (table private_conversation_preferences). */
@Entity
@Table(name = "private_conversation_preferences")
public class PrivateConversationPreference implements PersonalPreferences {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID otherId;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private boolean archived;

    private OffsetDateTime mutedUntil;

    @Column(nullable = false)
    private boolean mutedForever;

    protected PrivateConversationPreference() {
    }

    public PrivateConversationPreference(UUID userId, UUID otherId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.otherId = otherId;
    }

    @Override
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    @Override
    public void setArchived(boolean archived) { this.archived = archived; }

    @Override
    public void mute(OffsetDateTime until, boolean forever) {
        this.mutedUntil = forever ? null : until;
        this.mutedForever = forever;
    }

    public UUID getUserId() { return userId; }
    public UUID getOtherId() { return otherId; }
    @Override public boolean isPinned() { return pinned; }
    @Override public boolean isArchived() { return archived; }
    @Override public OffsetDateTime getMutedUntil() { return mutedUntil; }
    @Override public boolean isMutedForever() { return mutedForever; }
}
