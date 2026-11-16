package com.uasz.alumni.alumnistudentcompany.service;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Appelle POST /notifications/send de notification-messaging-service avec le JWT de l'appelant. */
@Component
public class NotificationClient {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient http;

    public NotificationClient(@Value("${notification.base-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Best effort : un échec est journalisé mais n'annule pas l'action métier. */
    public void notifier(UUID userId, String titre, String contenu, UUID referenceId, String bearerToken) {
        try {
            http.post().uri("/notifications/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .body(Map.of(
                    "userId", userId,
                    "type", "SYSTEM_ALERT", // pas de type dédié dans le contrat
                    "channel", "IN_APP",
                    "title", titre,
                    "content", contenu,
                    "referenceId", referenceId.toString()))
                .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            LOG.warn("Notification non envoyée à l'étudiant {} : {}", userId, e.getMessage());
        }
    }
}
