package com.uasz.alumni.analyticsmatching.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import com.uasz.alumni.analyticsmatching.service.SkillsGapService;
import com.uasz.alumni.analyticsmatching.spi.api.AnalyticsReportsApi;
import com.uasz.alumni.analyticsmatching.spi.model.InsertionReportResponse;
import com.uasz.alumni.analyticsmatching.spi.model.SkillsGapResponse;

@RestController
public class AnalyticsReportsController implements AnalyticsReportsApi {

    private final SkillsGapService skillsGapService;

    public AnalyticsReportsController(SkillsGapService skillsGapService) {
        this.skillsGapService = skillsGapService;
    }

    /** AL-40. Accès : administrateur et enseignant (à valider dans la matrice des rôles). */
    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'ENSEIGNANT')")
    public ResponseEntity<SkillsGapResponse> getSkillsGapReport() {
        return ResponseEntity.ok(skillsGapService.buildReport());
    }

    /** Hors périmètre AL-40 (AL-39) : l'interface générée impose de déclarer la méthode. */
    @Override
    public ResponseEntity<InsertionReportResponse> getInsertionReport(Integer graduationYear, String specialty) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
