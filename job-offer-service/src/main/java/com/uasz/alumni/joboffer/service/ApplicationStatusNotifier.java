package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.config.AsyncResilienceConfig;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifie l'étudiant (APPLICATION_STATUS_CHANGED) quand le statut de sa candidature change.
 *
 * <p>Exécuté <b>après le commit</b> (aucune notification si la transaction échoue) et <b>en asynchrone</b> : la
 * réponse au recruteur n'attend ni le service de notification ni ses retries. Un échec définitif est journalisé
 * et n'annule jamais le changement de statut (ADR-0006, ALUM-83).
 */
@Component
public class ApplicationStatusNotifier {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStatusNotifier.class);

    static final String TITLE = "Mise à jour de votre candidature";

    private static final Map<ApplicationStatus, String> LABELS = Map.of(
            ApplicationStatus.SUBMITTED, "soumise",
            ApplicationStatus.UNDER_REVIEW, "en cours d'examen",
            ApplicationStatus.SHORTLISTED, "présélectionnée",
            ApplicationStatus.ACCEPTED, "acceptée",
            ApplicationStatus.REJECTED, "refusée");

    private final NotificationClient notifications;

    public ApplicationStatusNotifier(NotificationClient notifications) {
        this.notifications = notifications;
    }

    @Async(AsyncResilienceConfig.INTER_SERVICE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ApplicationStatusChanged event) {
        SendNotificationRequest request = new SendNotificationRequest(
                event.studentId(),
                NotificationType.APPLICATION_STATUS_CHANGED,
                TITLE,
                "Votre candidature à l'offre « %s » est désormais %s."
                        .formatted(event.offerTitle(), LABELS.get(event.newStatus())));
        request.setChannel(NotificationChannel.IN_APP);
        request.setReferenceId(event.applicationId().toString());
        try {
            notifications.send(request);
            log.info("Notification APPLICATION_STATUS_CHANGED envoyée (candidature {})", event.applicationId());
        } catch (RuntimeException e) {
            // Retries épuisés ou erreur non transitoire : le changement de statut, déjà validé, reste acquis.
            log.error("Notification non délivrée pour la candidature {} : {}", event.applicationId(), e.toString());
        }
    }
}
