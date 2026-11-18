package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.uasz.alumni.analyticsmatching.spi.model.SkillsGapItem;
import com.uasz.alumni.analyticsmatching.spi.model.SkillsGapResponse;

/** SkillsGapService sans Spring : la source de données est une fausse implémentation en mémoire. */
class SkillsGapServiceTest {

    /** Fausse source : renvoie les listes fournies. */
    private record FakeSource(List<List<String>> offers, List<List<String>> profiles) implements SkillsDataSource {
        @Override
        public List<List<String>> skillsRequiredByOffers() {
            return offers;
        }

        @Override
        public List<List<String>> skillsOfStudentsAndAlumni() {
            return profiles;
        }
    }

    @Test
    void mappeChaqueStatistiqueVersUnSkillsGapItem() {
        SkillsGapService service = new SkillsGapService(new FakeSource(
                List.of(List.of("Java", "Docker"), List.of("Java")),
                List.of(List.of("Java"), List.of("React"))));

        SkillsGapResponse response = service.buildReport();

        assertThat(response.getSkillsGapAnalysis())
                .extracting(SkillsGapItem::getSkillName, SkillsGapItem::getDemandPercentage,
                        SkillsGapItem::getSupplyPercentage, SkillsGapItem::getGap)
                .containsExactly(
                        tuple("Java", 100.0f, 50.0f, 50.0f),
                        tuple("Docker", 50.0f, 0.0f, 50.0f));
    }

    @Test
    void topRequiredSkillsEstLimiteAuxCinqPlusDemandees() {
        // 6 compétences : "F" est la moins demandée et doit être exclue du top 5.
        SkillsGapService service = new SkillsGapService(new FakeSource(
                List.of(List.of("A", "B", "C", "D", "E", "F"), List.of("A", "B", "C", "D", "E")),
                List.of()));

        SkillsGapResponse response = service.buildReport();

        assertThat(response.getTopRequiredSkills()).containsExactly("A", "B", "C", "D", "E");
        assertThat(response.getSkillsGapAnalysis()).hasSize(6);
    }
}
