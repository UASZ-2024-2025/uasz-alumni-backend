package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifie l'étudiant (APPLICATION_STATUS_CHANGED) une fois le changement de statut COMMITÉ :
 * aucune notification n'est envoyée si la transaction échoue.
 */
@Component
public class ApplicationStatusNotifier {

    static final String TYPE = "APPLICATION_STATUS_CHANGED";

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ApplicationStatusChanged event) {
        notifications.send(event.studentId(), TYPE,
                "Mise à jour de votre candidature",
                "Votre candidature à l'offre « %s » est désormais %s."
                        .formatted(event.offerTitle(), LABELS.get(event.newStatus())),
                event.applicationId(), event.bearerToken());
    }
}
