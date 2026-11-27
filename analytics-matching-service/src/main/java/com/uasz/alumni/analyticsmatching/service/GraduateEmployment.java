package com.uasz.alumni.analyticsmatching.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Situation d'emploi d'un diplômé, indépendante des classes générées du SPI.
 *
 * @param employed             a une expérience professionnelle en cours
 * @param experienceStartDates dates de début de toutes ses expériences
 */
public record GraduateEmployment(int graduationYear, String specialty, boolean employed,
                                 List<LocalDate> experienceStartDates) {

    public GraduateEmployment {
        experienceStartDates = experienceStartDates == null ? List.of() : List.copyOf(experienceStartDates);
    }
}
