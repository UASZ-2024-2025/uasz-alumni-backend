package com.uasz.alumni.analyticsmatching.service;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.uasz.alumni.analyticsmatching.exceptions.NotImplementedYetException;

/**
 * Hors profils local/dev : répond 501 tant que job-offer-service et alumni-student-company-service
 * ne sont pas branchés (ALUM-83).
 */
@Component
@Profile("!local & !dev")
public class UnavailableSkillsDataSource implements SkillsDataSource {

    @Override
    public List<List<String>> skillsRequiredByOffers() {
        throw new NotImplementedYetException("getSkillsGapReport");
    }

    @Override
    public List<List<String>> skillsOfStudentsAndAlumni() {
        throw new NotImplementedYetException("getSkillsGapReport");
    }
}
