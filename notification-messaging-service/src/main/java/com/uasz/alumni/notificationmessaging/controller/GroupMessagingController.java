package com.uasz.alumni.notificationmessaging.controller;

import com.uasz.alumni.notificationmessaging.exceptions.NotImplementedYetException;
import com.uasz.alumni.notificationmessaging.spi.api.GroupMessagingApi;
import com.uasz.alumni.notificationmessaging.spi.model.Group;
import com.uasz.alumni.notificationmessaging.spi.model.GroupCreateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMembersRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessage;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessagePagedResponse;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSummary;
import com.uasz.alumni.notificationmessaging.spi.model.GroupUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.SendMessageRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GroupMessagingController implements GroupMessagingApi {

    @Override
    public ResponseEntity<List<GroupSummary>> listGroups() {
        // TODO : groupes de l'utilisateur connecté (dernier message + non-lus)
        throw new NotImplementedYetException("listGroups");
    }

    @Override
    public ResponseEntity<Group> createGroup(GroupCreateRequest groupCreateRequest) {
        // TODO : créer le groupe, l'utilisateur courant devient OWNER -> 201 / 400
        throw new NotImplementedYetException("createGroup");
    }

    @Override
    public ResponseEntity<Group> getGroupById(UUID groupId) {
        // TODO : détail + membres -> 200 / 403 / 404
        throw new NotImplementedYetException("getGroupById");
    }

    @Override
    public ResponseEntity<Group> updateGroup(UUID groupId, GroupUpdateRequest groupUpdateRequest) {
        // TODO : propriétaire uniquement -> 200 / 400 / 403 / 404
        throw new NotImplementedYetException("updateGroup");
    }

    @Override
    public ResponseEntity<Void> deleteGroup(UUID groupId) {
        // TODO : propriétaire ou administrateur -> 204 / 403 / 404
        throw new NotImplementedYetException("deleteGroup");
    }

    @Override
    public ResponseEntity<Group> addGroupMembers(UUID groupId, GroupMembersRequest groupMembersRequest) {
        // TODO : propriétaire uniquement + GROUP_INVITATION -> 200 / 400 / 403 / 404
        throw new NotImplementedYetException("addGroupMembers");
    }

    @Override
    public ResponseEntity<Void> removeGroupMember(UUID groupId, UUID userId) {
        // TODO : propriétaire (n'importe qui) ou membre (lui-même) -> 204 / 403 / 404
        throw new NotImplementedYetException("removeGroupMember");
    }

    @Override
    public ResponseEntity<GroupMessagePagedResponse> getGroupMessages(UUID groupId, Integer page, Integer size) {
        // TODO : historique paginé (membres uniquement) -> 200 / 403 / 404
        throw new NotImplementedYetException("getGroupMessages");
    }

    @Override
    public ResponseEntity<GroupMessage> sendGroupMessage(UUID groupId, SendMessageRequest sendMessageRequest) {
        // TODO : envoyer + GROUP_MESSAGE_RECEIVED -> 201 / 400 / 403 / 404
        throw new NotImplementedYetException("sendGroupMessage");
    }

    @Override
    public ResponseEntity<Void> markGroupAsRead(UUID groupId) {
        // TODO : marquer le groupe comme lu -> 204 / 403 / 404
        throw new NotImplementedYetException("markGroupAsRead");
    }
}
