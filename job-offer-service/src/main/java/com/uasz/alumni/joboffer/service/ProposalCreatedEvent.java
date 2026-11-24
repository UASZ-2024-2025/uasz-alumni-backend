package com.uasz.alumni.joboffer.service;

import java.util.UUID;

/** Événement émis lors de la création d'une proposition directe (ALUM-174). */
public record ProposalCreatedEvent(UUID proposalId, UUID studentId, String offerTitle, String recruiterName) {
}
