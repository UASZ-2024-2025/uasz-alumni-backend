package com.uasz.alumni.joboffer.security;

import java.util.UUID;

/** Utilisateur authentifié : identifiant (claim {@code sub}) et rôle (claim {@code role}). */
public record CurrentUser(UUID id, String role) {

    public boolean isAdmin() {
        return "ADMINISTRATEUR".equals(role);
    }
}
