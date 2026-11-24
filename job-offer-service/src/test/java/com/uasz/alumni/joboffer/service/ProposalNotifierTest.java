package com.uasz.alumni.joboffer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProposalNotifierTest {

    private final NotificationClient client = mock(NotificationClient.class);
    private final ProposalNotifier notifier = new ProposalNotifier(client);

    @Test
    void notifieEtudiantAvecTypeNewJobProposal() {
        UUID proposalId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        notifier.onProposalCreated(new ProposalCreatedEvent(proposalId, studentId, "Stage Java", "Atos SN"));

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(client).send(captor.capture());
        SendNotificationRequest req = captor.getValue();
        assertEquals(studentId, req.getUserId());
        assertEquals(NotificationType.NEW_JOB_PROPOSAL, req.getType());
        assertEquals(NotificationChannel.IN_APP, req.getChannel());
        assertEquals(proposalId.toString(), req.getReferenceId());
    }

    @Test
    void notifieRecruteurQuandEtudiantRepond() {
        UUID proposalId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();

        notifier.onProposalResponded(
                new ProposalRespondedEvent(proposalId, recruiterId, "Awa Ndiaye", "Stage Java", ProposalStatus.ACCEPTED));

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(client).send(captor.capture());
        assertEquals(recruiterId, captor.getValue().getUserId());
        assertEquals(NotificationType.NEW_JOB_PROPOSAL, captor.getValue().getType());
    }

    @Test
    void echecNotificationNeRemontePasDException() {
        doThrow(new RuntimeException("Service indisponible")).when(client).send(any());
        assertDoesNotThrow(() -> notifier.onProposalCreated(
                new ProposalCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "Stage", "Recruteur")));
    }
}
