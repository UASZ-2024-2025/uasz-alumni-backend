package com.uasz.alumni.joboffer.service;

import java.util.UUID;

/** Événement publié quand un étudiant dépose une candidature : le recruteur (auteur de l'offre) en est notifié. */
public record ApplicationSubmitted(UUID applicationId, UUID recruiterId, String offerTitle, String studentName) {
}
