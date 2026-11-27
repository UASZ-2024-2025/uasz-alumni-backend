package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.service.CompanyHomeService;
import com.uasz.alumni.joboffer.spi.api.CompanyHomeApi;
import com.uasz.alumni.joboffer.spi.model.CompanyHomeSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** Synthèse d'accueil de l'entreprise connectée (ALUM-249). Route et réponses définies par le contrat. */
@RestController
public class CompanyHomeController implements CompanyHomeApi {

    private final CompanyHomeService service;

    public CompanyHomeController(CompanyHomeService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("hasRole('ENTREPRISE')")
    public ResponseEntity<CompanyHomeSummary> getMyCompanyHomeSummary() {
        return ResponseEntity.ok(service.summaryFor(currentUser()));
    }

    private static AuthenticatedUser currentUser() {
        return (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
