package com.uasz.alumni.analyticsmatching.service;

/** Source des compteurs bruts pour le tableau de bord administrateur (ALUM-182). */
public interface DashboardStatsDataSource {

    PlatformCounts fetchCounts();

    record PlatformCounts(int alumniCount,
                          int employedAlumniCount,
                          int studentCount,
                          int companyCount,
                          int internshipCount,
                          int jobOfferCount) {
    }
}
