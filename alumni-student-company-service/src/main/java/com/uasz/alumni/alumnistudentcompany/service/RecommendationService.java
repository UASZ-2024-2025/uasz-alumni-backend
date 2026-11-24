package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.model.Recommendation;
import com.uasz.alumni.alumnistudentcompany.repository.RecommendationRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private final RecommendationRepository repository;
    private final ApplicationEventPublisher events;

    public RecommendationService(RecommendationRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /** Enregistre la recommandation ; l'étudiant est notifié après le commit (voir RecommendationNotifier). */
    @Transactional
    public Recommendation recommander(UUID recommenderId, UUID studentId, UUID companyId, String comments) {
        Recommendation saved = repository.save(new Recommendation(recommenderId, studentId, companyId, comments));
        events.publishEvent(new RecommendationCreated(saved.getId(), studentId));
        return saved;
    }
}
