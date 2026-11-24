package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.config.AsyncResilienceConfig;
import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
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
 * Notifie l'étudiant ({@code NEW_JOB_PROPOSAL}) lors d'une proposition directe, et le recruteur
 * lorsque l'étudiant accepte ou refuse la proposition (ALUM-174).
 */
@Component
public class ProposalNotifier {

    private static final Logger log = LoggerFactory.getLogger(ProposalNotifier.class);

    static final String NEW_PROPOSAL_TITLE = "Nouvelle proposition d'offre";
    static final String RESPONSE_TITLE = "Réponse à votre proposition d'offre";

    private final NotificationClient notifications;

    public ProposalNotifier(NotificationClient notifications) {
        this.notifications = notifications;
    }

    @Async(AsyncResilienceConfig.INTER_SERVICE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProposalCreated(ProposalCreatedEvent event) {
        String sender = event.recruiterName() != null && !event.recruiterName().isBlank()
                ? event.recruiterName()
                : "Un recruteur";
        SendNotificationRequest request = new SendNotificationRequest(
                event.studentId(),
                NotificationType.NEW_JOB_PROPOSAL,
                NEW_PROPOSAL_TITLE,
                "%s vous propose directement l'offre « %s ».".formatted(sender, event.offerTitle()));
        request.setChannel(NotificationChannel.IN_APP);
        request.setReferenceId(event.proposalId().toString());
        sendSafely(request, event.proposalId(), "étudiant");
    }

    @Async(AsyncResilienceConfig.INTER_SERVICE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProposalResponded(ProposalRespondedEvent event) {
        if (event.recruiterId() == null) {
            return;
        }
        String student = event.studentName() != null && !event.studentName().isBlank()
                ? event.studentName()
                : "L'étudiant";
        String action = event.newStatus() == ProposalStatus.ACCEPTED ? "accepté" : "refusé";
        SendNotificationRequest request = new SendNotificationRequest(
                event.recruiterId(),
                NotificationType.NEW_JOB_PROPOSAL,
                RESPONSE_TITLE,
                "%s a %s votre proposition pour l'offre « %s ».".formatted(student, action, event.offerTitle()));
        request.setChannel(NotificationChannel.IN_APP);
        request.setReferenceId(event.proposalId().toString());
        sendSafely(request, event.proposalId(), "recruteur");
    }

    private void sendSafely(SendNotificationRequest request, java.util.UUID proposalId, String targetLabel) {
        try {
            notifications.send(request);
            log.info("Notification NEW_JOB_PROPOSAL envoyée ({} - proposition {})", targetLabel, proposalId);
        } catch (RuntimeException e) {
            // La transaction est déjà validée ; l'échec de notification ne l'annule jamais (ADR-0006).
            log.error("Notification non délivrée ({} - proposition {}) : {}", targetLabel, proposalId, e.toString());
        }
    }
}
