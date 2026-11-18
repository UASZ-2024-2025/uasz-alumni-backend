package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.model.ChatGroup;
import com.uasz.alumni.notificationmessaging.model.ChatGroupMember;
import com.uasz.alumni.notificationmessaging.model.ChatGroupMessage;
import com.uasz.alumni.notificationmessaging.model.ChatUser;
import com.uasz.alumni.notificationmessaging.spi.model.Group;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMember;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessage;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferences;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSettings;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Conversion des entités vers les modèles du contrat (noms résolus via l'annuaire local). */
final class GroupViews {

    private GroupViews() {
    }

    static Group group(ChatGroup g, List<ChatGroupMember> members, ChatGroupMember me,
                       Map<UUID, ChatUser> users, OffsetDateTime now) {
        return new Group()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .ownerId(g.getOwnerId())
                .members(members.stream().map(m -> member(m, users.get(m.getUserId()))).toList())
                .settings(settings(g))
                .preferences(preferences(me, now))
                .createdAt(g.getCreatedAt());
    }

    static GroupMember member(ChatGroupMember m, ChatUser user) {
        return new GroupMember()
                .userId(m.getUserId())
                .userName(user != null ? user.getDisplayName() : null)
                .userRole(user != null ? user.getAppRole() : null)
                .groupRole(m.getGroupRole())
                .joinedAt(m.getJoinedAt());
    }

    static GroupSettings settings(ChatGroup g) {
        return new GroupSettings()
                .onlyAdminsCanSend(g.isOnlyAdminsCanSend())
                .onlyAdminsCanEditInfo(g.isOnlyAdminsCanEditInfo())
                .onlyAdminsCanAddMembers(g.isOnlyAdminsCanAddMembers());
    }

    static GroupPreferences preferences(ChatGroupMember me, OffsetDateTime now) {
        boolean muted = me.isMuted(now);
        return new GroupPreferences()
                .pinned(me.isPinned())
                .archived(me.isArchived())
                .muted(muted)
                .mutedUntil(muted && !me.isMutedForever() ? me.getMutedUntil() : null);
    }

    static GroupMessage message(ChatGroupMessage m, ChatUser sender) {
        return new GroupMessage()
                .id(m.getId())
                .groupId(m.getGroupId())
                .senderId(m.getSenderId())
                .senderName(sender != null ? sender.getDisplayName() : null)
                .type(m.getType())
                .content(m.getContent())
                .deleted(m.isDeleted())
                .createdAt(m.getCreatedAt());
    }
}
