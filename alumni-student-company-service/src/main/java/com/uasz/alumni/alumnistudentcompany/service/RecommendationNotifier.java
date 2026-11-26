package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.config.AsyncResilienceConfig;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Prévient l'étudiant recommandé. Après le commit et en asynchrone : la réponse à l'Alumni n'attend pas
 * le service de notification ; un échec est journalisé et n'annule jamais la recommandation (ADR-0006).
 */
@Component
public class RecommendationNotifier {

    private static final Logger log = LoggerFactory.getLogger(RecommendationNotifier.class);

    static final String TITLE = "Nouvelle recommandation";
    static final String CONTENT = "Un Alumni vous a recommandé auprès d'une entreprise.";

    private final NotificationClient notifications;

    public RecommendationNotifier(NotificationClient notifications) {
        this.notifications = notifications;
    }

    @Async(AsyncResilienceConfig.INTER_SERVICE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(RecommendationCreated event) {
        // pas de type dédié dans le contrat : SYSTEM_ALERT
        SendNotificationRequest request = new SendNotificationRequest(
                event.studentId(), NotificationType.SYSTEM_ALERT, TITLE, CONTENT);
        request.setChannel(NotificationChannel.IN_APP);
        request.setReferenceId(event.recommendationId().toString());
        try {
            notifications.send(request);
        } catch (RuntimeException e) {
            log.warn("Notification non envoyée à l'étudiant {} : {}", event.studentId(), e.toString());
        }
    }
}
