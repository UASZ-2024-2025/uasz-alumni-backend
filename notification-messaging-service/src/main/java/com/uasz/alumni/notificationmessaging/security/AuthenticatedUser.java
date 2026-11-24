package com.uasz.alumni.notificationmessaging.security;

import java.util.UUID;

/**
 * Utilisateur authentifié par son jeton (principal Spring Security).
 * {@code name} (claim "name", prénom et nom) peut être absent des jetons émis avant son ajout.
 */
public record AuthenticatedUser(UUID id, String email, String role, String name) {

    public AuthenticatedUser(UUID id, String email, String role) {
        this(id, email, role, null);
    }
}
