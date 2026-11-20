package com.uasz.alumni.joboffer.security;

import java.util.UUID;

/**
 * Utilisateur authentifié par son jeton (principal Spring Security).
 *
 * @param name      nom affiché (claim {@code name}), absent des jetons anciens
 * @param companyId fiche entreprise du compte ENTREPRISE rattaché (claim {@code companyId}, ALUM-168), sinon null
 */
public record AuthenticatedUser(UUID id, String email, String role, String name, UUID companyId) {

    public AuthenticatedUser(UUID id, String email, String role) {
        this(id, email, role, null, null);
    }
}
