package com.uasz.alumni.alumnistudentcompany.security;

import java.util.UUID;

/** Utilisateur authentifié par son jeton (principal Spring Security). */
public record AuthenticatedUser(UUID id, String email, String role) {
}
