package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import java.util.UUID;

/** Événement publié à chaque changement de statut d'une candidature. */
public record ApplicationStatusChanged(UUID applicationId, UUID studentId, String offerTitle,
                                       ApplicationStatus newStatus, String bearerToken) {
}
