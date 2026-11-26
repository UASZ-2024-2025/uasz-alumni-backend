package com.uasz.alumni.alumnistudentcompany.service;

import java.util.UUID;

/** Publié quand une recommandation est enregistrée. */
public record RecommendationCreated(UUID recommendationId, UUID studentId) {
}
