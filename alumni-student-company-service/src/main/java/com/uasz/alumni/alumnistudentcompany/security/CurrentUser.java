package com.uasz.alumni.alumnistudentcompany.security;

import java.util.UUID;

/** Utilisateur authentifié, extrait du JWT : identifiant du user-service et statut administrateur. */
public record CurrentUser(UUID userId, boolean admin) {
}
