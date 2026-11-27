package com.uasz.alumni.joboffer.service;

import java.util.UUID;

/** Nombre de propositions directes envoyées par un recruteur (ALUM-174 fournira l'implémentation réelle). */
@FunctionalInterface
public interface SentProposalsCounter {

    long countSentBy(UUID recruiterId);
}
