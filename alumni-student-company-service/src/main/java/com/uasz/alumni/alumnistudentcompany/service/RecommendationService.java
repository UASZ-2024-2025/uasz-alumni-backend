package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.model.Recommendation;
import com.uasz.alumni.alumnistudentcompany.repository.RecommendationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {

    private final RecommendationRepository repository;
    private final NotificationClient notifications;

    public RecommendationService(RecommendationRepository repository, NotificationClient notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }

    public Recommendation recommander(UUID recommenderId, UUID studentId, UUID companyId, String comments, String bearerToken) {
        Recommendation saved = repository.save(new Recommendation(recommenderId, studentId, companyId, comments));
        notifications.notifier(studentId, "Nouvelle recommandation",
            "Un Alumni vous a recommandé auprès d'une entreprise.", saved.getId(), bearerToken);
        return saved;
    }
}
