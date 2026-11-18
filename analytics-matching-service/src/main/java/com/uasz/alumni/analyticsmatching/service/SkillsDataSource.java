package com.uasz.alumni.analyticsmatching.service;

import java.util.List;

/**
 * Source des compétences utilisées par le rapport skills gap (ALUM-164).
 * Profils local/dev : {@link SampleSkillsDataSource} (données d'exemple) ; ailleurs :
 * {@link UnavailableSkillsDataSource} (501). Une implémentation interrogeant job-offer-service
 * et alumni-student-company-service viendra avec la communication inter-services (ALUM-83).
 */
public interface SkillsDataSource {

    /** Une liste de compétences par offre d'emploi/stage. */
    List<List<String>> skillsRequiredByOffers();

    /** Une liste de compétences par étudiant/alumni. */
    List<List<String>> skillsOfStudentsAndAlumni();
}
