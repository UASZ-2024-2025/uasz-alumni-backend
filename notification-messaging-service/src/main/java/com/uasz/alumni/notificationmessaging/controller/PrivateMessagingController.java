package com.uasz.alumni.notificationmessaging.controller;

import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import com.uasz.alumni.notificationmessaging.security.CurrentUser;
import com.uasz.alumni.notificationmessaging.service.ChatUserDirectory;
import com.uasz.alumni.notificationmessaging.service.PrivateMessagingService;
import com.uasz.alumni.notificationmessaging.spi.api.PrivateMessagingApi;
import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
import com.uasz.alumni.notificationmessaging.spi.model.EditMessageRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferences;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferencesUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessage;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessagePagedResponse;
import com.uasz.alumni.notificationmessaging.spi.model.SendMessageRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivateMessagingController implements PrivateMessagingApi {

    private final PrivateMessagingService service;
    private final ChatUserDirectory directory;

    public PrivateMessagingController(PrivateMessagingService service, ChatUserDirectory directory) {
        this.service = service;
        this.directory = directory;
    }

    /** Appelant courant ; son nom est enregistré pour que ses interlocuteurs le voient (comme pour les groupes). */
    private UUID me() {
        AuthenticatedUser user = CurrentUser.get();
        directory.remember(user);
        return user.id();
    }

    @Override
    public ResponseEntity<List<ConversationSummary>> listConversations() {
        return ResponseEntity.ok(service.listConversations(me()));
    }

    @Override
    public ResponseEntity<PrivateMessagePagedResponse> getConversationMessages(
            UUID recipientId, Integer page, Integer size) {
        return ResponseEntity.ok(service.getConversation(me(), recipientId, page, size));
    }

    @Override
    public ResponseEntity<PrivateMessage> sendPrivateMessage(
            UUID recipientId, SendMessageRequest sendMessageRequest) {
        PrivateMessage created = service.send(me(), recipientId, sendMessageRequest.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<Void> markConversationAsRead(UUID recipientId) {
        service.markAsRead(me(), recipientId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GroupPreferences> updateConversationPreferences(
            UUID recipientId, GroupPreferencesUpdateRequest request) {
        return ResponseEntity.ok(service.updatePreferences(me(), recipientId, request));
    }

    @Override
    public ResponseEntity<PrivateMessage> editPrivateMessage(UUID messageId, EditMessageRequest request) {
        return ResponseEntity.ok(service.edit(me(), messageId, request.getContent()));
    }

    @Override
    public ResponseEntity<Void> deletePrivateMessage(UUID messageId) {
        service.delete(me(), messageId);
        return ResponseEntity.noContent().build();
    }
}
