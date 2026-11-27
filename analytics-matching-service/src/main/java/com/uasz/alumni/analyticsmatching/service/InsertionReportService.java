package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.service.InsertionCalculator.GroupStat;
import com.uasz.alumni.analyticsmatching.service.InsertionCalculator.Result;
import com.uasz.alumni.analyticsmatching.spi.model.InsertionReportItem;
import com.uasz.alumni.analyticsmatching.spi.model.InsertionReportResponse;
import java.time.MonthDay;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Rapport du taux d'insertion professionnelle (ALUM-183, GET /analytics/reports/insertion). */
@Service
public class InsertionReportService {

    /** Le contrat ne porte que l'année de diplôme : la promotion est réputée diplômée le 1er juillet. */
    static final MonthDay GRADUATION_REFERENCE = MonthDay.of(7, 1);
    static final int MIN_YEAR = 1990;
    static final int MAX_YEAR = 2100;
    static final int SPECIALTY_MAX = 150;

    private final InsertionDataSource dataSource;

    public InsertionReportService(InsertionDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public InsertionReportResponse buildReport(Integer graduationYear, String specialty) {
        if (graduationYear != null && (graduationYear < MIN_YEAR || graduationYear > MAX_YEAR)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRADUATION_YEAR",
                    "L'année de diplôme doit être comprise entre " + MIN_YEAR + " et " + MAX_YEAR + ".");
        }
        String filter = specialty == null || specialty.isBlank() ? null : specialty.strip();
        if (filter != null && filter.length() > SPECIALTY_MAX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SPECIALTY",
                    "La spécialité ne doit pas dépasser " + SPECIALTY_MAX + " caractères.");
        }

        Result result = InsertionCalculator.compute(dataSource.graduates(graduationYear, filter), GRADUATION_REFERENCE);

        InsertionReportResponse response = new InsertionReportResponse();
        response.setOverallInsertionRate(result.overallInsertionRate());
        response.setReportData(result.groups().stream().map(InsertionReportService::toItem).toList());
        return response;
    }

    private static InsertionReportItem toItem(GroupStat stat) {
        InsertionReportItem item = new InsertionReportItem();
        item.setGraduationYear(stat.graduationYear());
        item.setSpecialty(stat.specialty());
        item.setTotalGraduates(stat.totalGraduates());
        item.setEmployedCount(stat.employedCount());
        item.setInsertionRate(stat.insertionRate());
        item.setAverageMonthsToFirstJob(stat.averageMonthsToFirstJob());
        return item;
    }
}
