package com.uasz.alumni.analyticsmatching.service;

import java.util.List;

/**
 * Source des diplômés pour le rapport d'insertion (ALUM-183). Implémentation : appel de
 * alumni-student-company-service ({@link AlumniServiceInsertionDataSource}).
 */
public interface InsertionDataSource {

    /** Un élément par diplômé ; les filtres sont facultatifs (null = pas de filtre). */
    List<GraduateEmployment> graduates(Integer graduationYear, String specialty);
}
