package com.uasz.alumni.joboffer.service;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Appelle POST /notifications/send de notification-messaging-service (contrat SendNotificationRequest)
 * en relayant le JWT de l'appelant.
 */
@Component
public class NotificationClient {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient http;

    public NotificationClient(@Value("${notification.base-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Best effort : un échec est journalisé mais n'annule pas le changement de statut déjà enregistré. */
    public void send(UUID userId, String type, String title, String content, UUID referenceId, String bearerToken) {
        try {
            http.post().uri("/notifications/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .body(Map.of(
                    "userId", userId,
                    "type", type,
                    "channel", "IN_APP",
                    "title", title,
                    "content", content,
                    "referenceId", referenceId.toString()))
                .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            LOG.warn("Notification {} non envoyée à l'utilisateur {} : {}", type, userId, e.getMessage());
        }
    }
}
