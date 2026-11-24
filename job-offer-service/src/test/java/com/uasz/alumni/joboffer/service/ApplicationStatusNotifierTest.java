package com.uasz.alumni.joboffer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

class ApplicationStatusNotifierTest {

    private final NotificationClient client = mock(NotificationClient.class);
    private final ApplicationStatusNotifier notifier = new ApplicationStatusNotifier(client);

    @Test
    void envoieUneNotificationApplicationStatusChangedALEtudiant() {
        UUID applicationId = UUID.randomUUID();
        UUID student = UUID.randomUUID();

        notifier.onStatusChanged(new ApplicationStatusChanged(applicationId, student, "Développeur Java",
                ApplicationStatus.SHORTLISTED));

        ArgumentCaptor<SendNotificationRequest> sent = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(client).send(sent.capture());
        assertThat(sent.getValue().getUserId()).isEqualTo(student);
        assertThat(sent.getValue().getType()).isEqualTo(NotificationType.APPLICATION_STATUS_CHANGED);
        assertThat(sent.getValue().getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(sent.getValue().getTitle()).isEqualTo("Mise à jour de votre candidature");
        assertThat(sent.getValue().getContent()).contains("Développeur Java", "présélectionnée");
        assertThat(sent.getValue().getReferenceId()).isEqualTo(applicationId.toString());
    }

    @Test
    void unePanneDuServiceDeNotificationNeRemontePas() {
        // retries épuisés : le changement de statut, déjà validé, ne doit pas être remis en cause
        doThrow(new ResourceAccessException("connexion refusée")).when(client).send(any());

        assertThatCode(() -> notifier.onStatusChanged(new ApplicationStatusChanged(UUID.randomUUID(),
                UUID.randomUUID(), "Développeur Java", ApplicationStatus.REJECTED)))
                .doesNotThrowAnyException();
    }

    @Test
    void previentLeRecruteurDUneNouvelleCandidature() {
        UUID applicationId = UUID.randomUUID();
        UUID recruiter = UUID.randomUUID();

        notifier.onSubmitted(new ApplicationSubmitted(applicationId, recruiter, "Développeur Java", "Awa Ndiaye"));

        ArgumentCaptor<SendNotificationRequest> sent = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(client).send(sent.capture());
        assertThat(sent.getValue().getUserId()).isEqualTo(recruiter);
        assertThat(sent.getValue().getTitle()).isEqualTo("Nouvelle candidature");
        assertThat(sent.getValue().getContent()).contains("Awa Ndiaye", "Développeur Java");
        assertThat(sent.getValue().getReferenceId()).isEqualTo(applicationId.toString());
    }

    @Test
    void unEchecDeNotificationNAnnulePasLaCandidature() {
        doThrow(new ResourceAccessException("indisponible")).when(client).send(any());
        assertThatCode(() -> notifier.onSubmitted(
                new ApplicationSubmitted(UUID.randomUUID(), UUID.randomUUID(), "Offre", "Awa"))).doesNotThrowAnyException();
    }
}
