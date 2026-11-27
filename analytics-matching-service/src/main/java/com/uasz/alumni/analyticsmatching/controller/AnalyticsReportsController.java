package com.uasz.alumni.analyticsmatching.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import com.uasz.alumni.analyticsmatching.service.InsertionReportService;
import com.uasz.alumni.analyticsmatching.service.SkillsGapService;
import com.uasz.alumni.analyticsmatching.spi.api.AnalyticsReportsApi;
import com.uasz.alumni.analyticsmatching.spi.model.InsertionReportResponse;
import com.uasz.alumni.analyticsmatching.spi.model.SkillsGapResponse;

@RestController
public class AnalyticsReportsController implements AnalyticsReportsApi {

    private final SkillsGapService skillsGapService;
    private final InsertionReportService insertionReportService;

    public AnalyticsReportsController(SkillsGapService skillsGapService,
                                      InsertionReportService insertionReportService) {
        this.skillsGapService = skillsGapService;
        this.insertionReportService = insertionReportService;
    }

    @Override
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<InsertionReportResponse> getInsertionReport(Integer graduationYear, String specialty) {
        return ResponseEntity.ok(insertionReportService.buildReport(graduationYear, specialty));
    }

    @Override
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<SkillsGapResponse> getSkillsGapReport() {
        return ResponseEntity.ok(skillsGapService.buildReport());
    }
}
