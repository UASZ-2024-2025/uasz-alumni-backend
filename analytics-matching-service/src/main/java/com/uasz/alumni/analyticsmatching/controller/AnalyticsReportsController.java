package com.uasz.alumni.analyticsmatching.controller;

import com.uasz.alumni.analyticsmatching.exceptions.NotImplementedYetException;
import com.uasz.alumni.analyticsmatching.spi.api.AnalyticsReportsApi;
import com.uasz.alumni.analyticsmatching.spi.model.InsertionReportResponse;
import com.uasz.alumni.analyticsmatching.spi.model.SkillsGapResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsReportsController implements AnalyticsReportsApi {

    @Override
    public ResponseEntity<InsertionReportResponse> getInsertionReport(Integer graduationYear, String specialty) {
        // TODO : rapport du taux d'insertion (ADMINISTRATEUR) -> 200 / 403
        throw new NotImplementedYetException("getInsertionReport");
    }

    @Override
    public ResponseEntity<SkillsGapResponse> getSkillsGapReport() {
        // TODO : analyse des écarts de compétences (ADMINISTRATEUR) -> 200 / 403
        throw new NotImplementedYetException("getSkillsGapReport");
    }
}
