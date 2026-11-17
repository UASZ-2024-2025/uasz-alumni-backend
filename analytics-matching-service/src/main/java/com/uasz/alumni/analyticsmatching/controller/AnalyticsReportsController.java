package com.uasz.alumni.analyticsmatching.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import com.uasz.alumni.analyticsmatching.exceptions.NotImplementedYetException;
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

    @Override
    public ResponseEntity<InsertionReportResponse> getInsertionReport(Integer graduationYear, String specialty) {
        // TODO : rapport du taux d'insertion (ADMINISTRATEUR) -> 200 / 403
        throw new NotImplementedYetException("getInsertionReport");
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'ENSEIGNANT')")
    public ResponseEntity<SkillsGapResponse> getSkillsGapReport() {
        return ResponseEntity.ok(skillsGapService.buildReport());
    }
}
