package com.uasz.alumni.notificationmessaging.model;

import com.uasz.alumni.notificationmessaging.spi.model.GroupMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Appartenance d'un utilisateur à un groupe (table chat_group_members) :
 * rôle dans le groupe, dernière lecture et préférences personnelles.
 */
@Entity
@Table(name = "chat_group_members")
public class ChatGroupMember {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupMemberRole groupRole;

    @Column(nullable = false)
    private OffsetDateTime joinedAt;

    private OffsetDateTime lastReadAt;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private boolean archived;

    private OffsetDateTime mutedUntil;

    @Column(nullable = false)
    private boolean mutedForever;

    protected ChatGroupMember() {
    }

    public ChatGroupMember(UUID groupId, UUID userId, GroupMemberRole groupRole, OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.userId = userId;
        this.groupRole = groupRole;
        this.joinedAt = now;
    }

    /** OWNER et ADMIN sont les administrateurs du groupe. */
    public boolean isGroupAdmin() {
        return groupRole == GroupMemberRole.OWNER || groupRole == GroupMemberRole.ADMIN;
    }

    public boolean isOwner() {
        return groupRole == GroupMemberRole.OWNER;
    }

    public boolean isMuted(OffsetDateTime now) {
        return mutedForever || (mutedUntil != null && mutedUntil.isAfter(now));
    }

    public void changeRole(GroupMemberRole role) { this.groupRole = role; }
    public void markRead(OffsetDateTime when) { this.lastReadAt = when; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public void mute(OffsetDateTime until, boolean forever) {
        this.mutedUntil = forever ? null : until;
        this.mutedForever = forever;
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public UUID getUserId() { return userId; }
    public GroupMemberRole getGroupRole() { return groupRole; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public OffsetDateTime getLastReadAt() { return lastReadAt; }
    public boolean isPinned() { return pinned; }
    public boolean isArchived() { return archived; }
    public OffsetDateTime getMutedUntil() { return mutedUntil; }
    public boolean isMutedForever() { return mutedForever; }
}
