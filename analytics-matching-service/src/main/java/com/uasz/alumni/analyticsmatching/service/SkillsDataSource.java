package com.uasz.alumni.analyticsmatching.service;

import java.util.List;

/**
 * Source des compétences utilisées par le rapport skills gap.
 * Aujourd'hui : jeu de test ({@link SampleSkillsDataSource}). Plus tard (AL-16) :
 * une implémentation qui interroge job-offer-service et alumni-student-company-service.
 */
public interface SkillsDataSource {

    /** Une liste de compétences par offre d'emploi/stage. */
    List<List<String>> skillsRequiredByOffers();

    /** Une liste de compétences par étudiant/alumni. */
    List<List<String>> skillsOfStudentsAndAlumni();
}
