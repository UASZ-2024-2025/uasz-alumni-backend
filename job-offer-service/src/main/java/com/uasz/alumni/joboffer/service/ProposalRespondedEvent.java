package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
import java.util.UUID;

/** Événement émis lorsque l'étudiant répond à une proposition directe (ALUM-174). */
public record ProposalRespondedEvent(UUID proposalId, UUID recruiterId, String studentName,
                                     String offerTitle, ProposalStatus newStatus) {
}
