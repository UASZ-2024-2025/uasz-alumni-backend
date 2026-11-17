package com.uasz.alumni.analyticsmatching.service;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * JEU DE TEST (ALUM-164) : actif uniquement avec les profils "local" et "dev".
 * Ailleurs, {@link UnavailableSkillsDataSource} répond 501 tant que les vraies sources
 * (job-offer-service et alumni-student-company-service) ne sont pas branchées.
 */
@Component
@Profile({"local", "dev"})
public class SampleSkillsDataSource implements SkillsDataSource {

    @Override
    public List<List<String>> skillsRequiredByOffers() {
        return List.of(
            List.of("Java", "Spring Boot", "Docker", "PostgreSQL"),
            List.of("Java", "Spring Boot", "Kubernetes"),
            List.of("React", "TypeScript", "Docker"),
            List.of("Java", "React", "PostgreSQL"),
            List.of("Spring Boot", "Docker", "Kubernetes", "CI/CD"));
    }

    @Override
    public List<List<String>> skillsOfStudentsAndAlumni() {
        return List.of(
            List.of("Java", "Spring Boot", "PostgreSQL"),
            List.of("Java", "React"),
            List.of("React", "TypeScript"),
            List.of("Java", "Docker"),
            List.of("Spring Boot", "PostgreSQL"));
    }
}
