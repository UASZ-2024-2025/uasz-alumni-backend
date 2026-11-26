package com.uasz.alumni.alumnistudentcompany.security;

import java.util.UUID;

/**
 * Utilisateur authentifié par son jeton (principal Spring Security). {@code companyId} : fiche entreprise d'un
 * compte ENTREPRISE rattaché (claim du jeton, ALUM-168), {@code null} sinon.
 */
public record AuthenticatedUser(UUID id, String email, String role, UUID companyId) {

    public AuthenticatedUser(UUID id, String email, String role) {
        this(id, email, role, null);
    }
}
