package com.uasz.alumni.alumnistudentcompany.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.model.Recommendation;
import com.uasz.alumni.alumnistudentcompany.repository.RecommendationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {

    private final RecommendationRepository repository = mock(RecommendationRepository.class);
    private final NotificationClient notifications = mock(NotificationClient.class);
    private final RecommendationService service = new RecommendationService(repository, notifications);

    @Test
    void enregistreLaRecommandationEtNotifieLEtudiant() {
        when(repository.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));
        UUID alumni = UUID.randomUUID();
        UUID etudiant = UUID.randomUUID();
        UUID entreprise = UUID.randomUUID();

        Recommendation r = service.recommander(alumni, etudiant, entreprise, "Très bon profil", "jeton");

        assertEquals(alumni, r.getRecommenderId());
        assertEquals(etudiant, r.getStudentId());
        verify(notifications).notifier(etudiant, "Nouvelle recommandation",
            "Un Alumni vous a recommandé auprès d'une entreprise.", r.getId(), "jeton");
    }
}
