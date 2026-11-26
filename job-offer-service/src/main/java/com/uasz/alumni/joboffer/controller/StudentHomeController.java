package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.service.StudentHomeSummaryService;
import com.uasz.alumni.joboffer.spi.api.StudentHomeApi;
import com.uasz.alumni.joboffer.spi.model.StudentHomeSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Synthèse d'accueil de l'étudiant connecté (ALUM-243). L'étudiant n'est identifié que par son jeton :
 * aucun identifiant en paramètre, chacun ne voit que ses chiffres.
 */
@RestController
public class StudentHomeController implements StudentHomeApi {

    private final StudentHomeSummaryService service;

    public StudentHomeController(StudentHomeSummaryService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<StudentHomeSummary> getMyStudentHomeSummary() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
        return ResponseEntity.ok(service.summary(user.id()));
    }
}
