package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.uasz.alumni.analyticsmatching.service.SkillsGapCalculator.SkillStat;

class SkillsGapCalculatorTest {

    // 4 offres et 4 profils : résultats calculés à la main.
    private final List<List<String>> offers = List.of(
            List.of("Java", "Docker", "Kubernetes"),
            List.of("Java", "Docker"),
            List.of("Java", "React"),
            List.of("Java"));
    private final List<List<String>> profiles = List.of(
            List.of("Java", "React"),
            List.of(" java ", "React"),
            List.of("Java"),
            List.of("Java", "docker"));

    @Test
    void calculeDemandeOffreEtEcart() {
        List<SkillStat> stats = SkillsGapCalculator.compute(offers, profiles);

        assertThat(stats).containsExactly(
                new SkillStat("Docker", 50.0f, 25.0f, 25.0f),
                new SkillStat("Kubernetes", 25.0f, 0.0f, 25.0f),
                new SkillStat("Java", 100.0f, 100.0f, 0.0f),
                new SkillStat("React", 25.0f, 50.0f, -25.0f));
    }

    @Test
    void ecartEstTrieParOrdreDecroissant() {
        List<SkillStat> stats = SkillsGapCalculator.compute(offers, profiles);

        assertThat(stats).extracting(SkillStat::gap).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void topRequiredRenvoieLesCompetencesLesPlusDemandees() {
        List<SkillStat> stats = SkillsGapCalculator.compute(offers, profiles);

        assertThat(SkillsGapCalculator.topRequired(stats, 2)).containsExactly("Java", "Docker");
    }

    @Test
    void sansOffreLeRapportEstVide() {
        assertThat(SkillsGapCalculator.compute(List.of(), profiles)).isEmpty();
    }

    @Test
    void sansProfilAucuneOffreNEstCouverte() {
        List<SkillStat> stats = SkillsGapCalculator.compute(List.of(List.of("Java")), List.of());

        assertThat(stats).containsExactly(new SkillStat("Java", 100.0f, 0.0f, 100.0f));
    }
}
