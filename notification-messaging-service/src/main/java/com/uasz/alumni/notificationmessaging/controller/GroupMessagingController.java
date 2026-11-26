package com.uasz.alumni.notificationmessaging.controller;

import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import com.uasz.alumni.notificationmessaging.service.ChatUserDirectory;
import com.uasz.alumni.notificationmessaging.service.GroupService;
import com.uasz.alumni.notificationmessaging.spi.api.GroupMessagingApi;
import com.uasz.alumni.notificationmessaging.spi.model.EditMessageRequest;
import com.uasz.alumni.notificationmessaging.spi.model.Group;
import com.uasz.alumni.notificationmessaging.spi.model.GroupCreateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMemberRoleUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMembersRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessage;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessagePagedResponse;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferences;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferencesUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSettings;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSummary;
import com.uasz.alumni.notificationmessaging.spi.model.GroupUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.SendMessageRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Groupes de discussion : réservés aux étudiants, Alumni, enseignants et administrateurs (403 pour ENTREPRISE).
 * Les règles fines (membre, administrateur du groupe, réglages) sont dans {@link GroupService}.
 */
@RestController
@PreAuthorize("hasAnyRole('ETUDIANT', 'ALUMNI', 'ENSEIGNANT', 'ADMINISTRATEUR')")
public class GroupMessagingController implements GroupMessagingApi {

    private final GroupService groups;
    private final ChatUserDirectory directory;

    public GroupMessagingController(GroupService groups, ChatUserDirectory directory) {
        this.groups = groups;
        this.directory = directory;
    }

    @Override
    public ResponseEntity<List<GroupSummary>> listGroups() {
        return ResponseEntity.ok(groups.listGroups(caller()));
    }

    @Override
    public ResponseEntity<Group> createGroup(GroupCreateRequest groupCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groups.createGroup(groupCreateRequest, caller()));
    }

    @Override
    public ResponseEntity<Group> getGroupById(UUID groupId) {
        return ResponseEntity.ok(groups.getGroup(groupId, caller()));
    }

    @Override
    public ResponseEntity<Group> updateGroup(UUID groupId, GroupUpdateRequest groupUpdateRequest) {
        return ResponseEntity.ok(groups.updateGroup(groupId, groupUpdateRequest, caller()));
    }

    @Override
    public ResponseEntity<Void> deleteGroup(UUID groupId) {
        groups.deleteGroup(groupId, caller());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Group> updateGroupSettings(UUID groupId, GroupSettings groupSettings) {
        return ResponseEntity.ok(groups.updateSettings(groupId, groupSettings, caller()));
    }

    @Override
    public ResponseEntity<GroupPreferences> updateGroupPreferences(UUID groupId,
                                                                   GroupPreferencesUpdateRequest request) {
        return ResponseEntity.ok(groups.updatePreferences(groupId, request, caller()));
    }

    @Override
    public ResponseEntity<Group> addGroupMembers(UUID groupId, GroupMembersRequest groupMembersRequest) {
        return ResponseEntity.ok(groups.addMembers(groupId, groupMembersRequest.getMemberIds(), caller()));
    }

    @Override
    public ResponseEntity<Group> updateGroupMemberRole(UUID groupId, UUID userId,
                                                       GroupMemberRoleUpdateRequest request) {
        return ResponseEntity.ok(groups.updateMemberRole(groupId, userId, request, caller()));
    }

    @Override
    public ResponseEntity<Void> removeGroupMember(UUID groupId, UUID userId) {
        groups.removeMember(groupId, userId, caller());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GroupMessagePagedResponse> getGroupMessages(UUID groupId, Integer page, Integer size) {
        return ResponseEntity.ok(groups.getMessages(groupId, page, size, caller()));
    }

    @Override
    public ResponseEntity<GroupMessage> sendGroupMessage(UUID groupId, SendMessageRequest sendMessageRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groups.sendMessage(groupId, sendMessageRequest, caller()));
    }

    @Override
    public ResponseEntity<GroupMessage> editGroupMessage(UUID groupId, UUID messageId, EditMessageRequest request) {
        return ResponseEntity.ok(groups.editMessage(groupId, messageId, request.getContent(), caller()));
    }

    @Override
    public ResponseEntity<Void> deleteGroupMessage(UUID groupId, UUID messageId) {
        groups.deleteMessage(groupId, messageId, caller());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> markGroupAsRead(UUID groupId) {
        groups.markAsRead(groupId, caller());
        return ResponseEntity.noContent().build();
    }

    /**
     * Appelant courant (la signature imposée par le SPI ne donne pas accès au jeton).
     * Son nom est enregistré dans l'annuaire local pour que les autres membres le voient.
     */
    private AuthenticatedUser caller() {
        AuthenticatedUser user = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        directory.remember(user);
        return user;
    }
}
