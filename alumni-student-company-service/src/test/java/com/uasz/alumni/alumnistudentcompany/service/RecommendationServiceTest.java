package com.uasz.alumni.alumnistudentcompany.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.model.Recommendation;
import com.uasz.alumni.alumnistudentcompany.repository.RecommendationRepository;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RecommendationServiceTest {

    private final RecommendationRepository repository = mock(RecommendationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RecommendationService service = new RecommendationService(repository, events);

    @Test
    void enregistreLaRecommandationEtPublieLEvenement() {
        when(repository.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));
        UUID alumni = UUID.randomUUID();
        UUID etudiant = UUID.randomUUID();

        Recommendation r = service.recommander(alumni, etudiant, UUID.randomUUID(), "Très bon profil");

        assertEquals(alumni, r.getRecommenderId());
        assertEquals(etudiant, r.getStudentId());
        verify(events).publishEvent(new RecommendationCreated(r.getId(), etudiant));
    }

    @Test
    void lNotifierEnvoieUneNotificationInAppALEtudiant() {
        NotificationClient client = mock(NotificationClient.class);
        UUID etudiant = UUID.randomUUID();
        UUID reco = UUID.randomUUID();

        new RecommendationNotifier(client).onCreated(new RecommendationCreated(reco, etudiant));

        ArgumentCaptor<SendNotificationRequest> sent = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(client).send(sent.capture());
        assertEquals(etudiant, sent.getValue().getUserId());
        assertEquals(NotificationType.SYSTEM_ALERT, sent.getValue().getType());
        assertEquals(reco.toString(), sent.getValue().getReferenceId());
    }

    @Test
    void lEchecDeNotificationNeRemontePas() {
        NotificationClient client = mock(NotificationClient.class);
        org.mockito.Mockito.doThrow(new RuntimeException("down")).when(client).send(any());

        new RecommendationNotifier(client).onCreated(new RecommendationCreated(UUID.randomUUID(), UUID.randomUUID()));
    }
}
