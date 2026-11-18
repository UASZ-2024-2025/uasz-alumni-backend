package com.uasz.alumni.notificationmessaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Groupe de discussion (table chat_groups) et ses réglages. */
@Entity
@Table(name = "chat_groups")
public class ChatGroup {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private boolean onlyAdminsCanSend;

    @Column(nullable = false)
    private boolean onlyAdminsCanEditInfo = true;

    @Column(nullable = false)
    private boolean onlyAdminsCanAddMembers = true;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime lastActivityAt;

    protected ChatGroup() {
    }

    public ChatGroup(String name, String description, UUID ownerId, OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.createdAt = now;
        this.lastActivityAt = now;
    }

    public void rename(String name) { this.name = name; }
    public void describe(String description) { this.description = description; }
    public void transferOwnership(UUID newOwnerId) { this.ownerId = newOwnerId; }
    public void touch(OffsetDateTime when) { this.lastActivityAt = when; }
    public void setOnlyAdminsCanSend(boolean value) { this.onlyAdminsCanSend = value; }
    public void setOnlyAdminsCanEditInfo(boolean value) { this.onlyAdminsCanEditInfo = value; }
    public void setOnlyAdminsCanAddMembers(boolean value) { this.onlyAdminsCanAddMembers = value; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public UUID getOwnerId() { return ownerId; }
    public boolean isOnlyAdminsCanSend() { return onlyAdminsCanSend; }
    public boolean isOnlyAdminsCanEditInfo() { return onlyAdminsCanEditInfo; }
    public boolean isOnlyAdminsCanAddMembers() { return onlyAdminsCanAddMembers; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastActivityAt() { return lastActivityAt; }
}
