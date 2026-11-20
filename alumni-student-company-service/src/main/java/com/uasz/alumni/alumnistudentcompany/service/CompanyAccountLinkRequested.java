package com.uasz.alumni.alumnistudentcompany.service;

import java.util.UUID;

/**
 * Publié quand un compte ENTREPRISE vient de créer sa fiche : le compte doit être rattaché à la fiche dans
 * user-service (ALUM-168). Traité après le commit, en asynchrone (ADR-0006).
 */
public record CompanyAccountLinkRequested(UUID userId, UUID companyId) {
}
