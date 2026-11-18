package com.uasz.alumni.notificationmessaging.controller;

import com.uasz.alumni.notificationmessaging.security.CurrentUser;
import com.uasz.alumni.notificationmessaging.service.PrivateMessagingService;
import com.uasz.alumni.notificationmessaging.spi.api.PrivateMessagingApi;
import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
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

    public PrivateMessagingController(PrivateMessagingService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<ConversationSummary>> listConversations() {
        return ResponseEntity.ok(service.listConversations(CurrentUser.id()));
    }

    @Override
    public ResponseEntity<PrivateMessagePagedResponse> getConversationMessages(
            UUID recipientId, Integer page, Integer size) {
        return ResponseEntity.ok(service.getConversation(CurrentUser.id(), recipientId, page, size));
    }

    @Override
    public ResponseEntity<PrivateMessage> sendPrivateMessage(
            UUID recipientId, SendMessageRequest sendMessageRequest) {
        PrivateMessage created = service.send(CurrentUser.id(), recipientId, sendMessageRequest.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<Void> markConversationAsRead(UUID recipientId) {
        service.markAsRead(CurrentUser.id(), recipientId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deletePrivateMessage(UUID messageId) {
        service.delete(CurrentUser.id(), messageId);
        return ResponseEntity.noContent().build();
    }
}
