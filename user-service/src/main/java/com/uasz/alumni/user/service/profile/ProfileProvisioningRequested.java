package com.uasz.alumni.user.service.profile;

import com.uasz.alumni.user.spi.model.RoleName;
import java.util.UUID;

/**
 * Publié quand un compte ETUDIANT ou ALUMNI vient d'être activé (confirmation de l'email) :
 * son profil doit être créé dans alumni-student-company-service (ALUM-83).
 */
public record ProfileProvisioningRequested(UUID userId, RoleName role, String firstName, String lastName,
                                           String email) {

    @Override
    public String toString() {
        // pas de données personnelles dans les logs
        return "ProfileProvisioningRequested[userId=" + userId + ", role=" + role + "]";
    }
}
