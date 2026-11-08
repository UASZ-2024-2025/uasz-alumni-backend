package com.uasz.alumni.joboffer.service.notification;

import com.uasz.alumni.joboffer.service.ApplicationStatusChanged;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
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
 * Notifie l'étudiant quand le statut de sa candidature change.
 *
 * <p>Exécuté <b>après le commit</b> (aucune notification pour une modification annulée) et <b>en
 * asynchrone</b> : la réponse HTTP au recruteur n'attend ni le service de notification ni ses retries.
 * Un échec définitif est journalisé et n'annule jamais le changement de statut (ADR-0006).
 */
@Component
public class ApplicationStatusNotifier {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStatusNotifier.class);

    private final NotificationClient client;

    public ApplicationStatusNotifier(NotificationClient client) {
        this.client = client;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ApplicationStatusChanged event) {
        SendNotificationRequest request = new SendNotificationRequest(
                event.studentUserId(),
                NotificationType.APPLICATION_STATUS_CHANGED,
                "Votre candidature a été mise à jour",
                "Votre candidature à l'offre « %s » est maintenant : %s."
                        .formatted(event.offerTitle(), label(event.newStatus())));
        request.setChannel(NotificationChannel.IN_APP);
        request.setReferenceId(event.applicationId().toString());
        try {
            client.send(request);
            log.info("Notification APPLICATION_STATUS_CHANGED envoyée (candidature {})", event.applicationId());
        } catch (Exception e) {
            // Retries épuisés ou erreur non transitoire : on n'interrompt pas le flux métier.
            log.error("Notification non délivrée pour la candidature {} après retries : {}",
                    event.applicationId(), e.toString());
        }
    }

    static String label(ApplicationStatus status) {
        return switch (status) {
            case SUBMITTED -> "envoyée";
            case UNDER_REVIEW -> "en cours d'examen";
            case SHORTLISTED -> "présélectionnée";
            case REJECTED -> "non retenue";
            case ACCEPTED -> "acceptée";
        };
    }
}
