package com.uasz.alumni.notificationmessaging.controller;

import com.uasz.alumni.notificationmessaging.exceptions.NotImplementedYetException;
import com.uasz.alumni.notificationmessaging.spi.api.PrivateMessagingApi;
import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessage;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessagePagedResponse;
import com.uasz.alumni.notificationmessaging.spi.model.SendMessageRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivateMessagingController implements PrivateMessagingApi {

    @Override
    public ResponseEntity<List<ConversationSummary>> listConversations() {
        // TODO : conversations de l'utilisateur connecté (dernier message + non-lus)
        throw new NotImplementedYetException("listConversations");
    }

    @Override
    public ResponseEntity<PrivateMessagePagedResponse> getConversationMessages(UUID recipientId, Integer page, Integer size) {
        // TODO : historique paginé -> 200 / 404
        throw new NotImplementedYetException("getConversationMessages");
    }

    @Override
    public ResponseEntity<PrivateMessage> sendPrivateMessage(UUID recipientId, SendMessageRequest sendMessageRequest) {
        // TODO : envoyer + déclencher MESSAGE_RECEIVED -> 201 / 400
        throw new NotImplementedYetException("sendPrivateMessage");
    }

    @Override
    public ResponseEntity<Void> markConversationAsRead(UUID recipientId) {
        // TODO : marquer la conversation comme lue -> 204
        throw new NotImplementedYetException("markConversationAsRead");
    }

    @Override
    public ResponseEntity<Void> deletePrivateMessage(UUID messageId) {
        // TODO : supprimer un message -> 204 / 404
        throw new NotImplementedYetException("deletePrivateMessage");
    }
}
