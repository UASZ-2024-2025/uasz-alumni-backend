package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import java.util.UUID;

/** Événement interne publié quand le statut d'une candidature change (traité après commit). */
public record ApplicationStatusChanged(UUID applicationId, UUID studentUserId, String offerTitle,
                                      ApplicationStatus newStatus) {
}
