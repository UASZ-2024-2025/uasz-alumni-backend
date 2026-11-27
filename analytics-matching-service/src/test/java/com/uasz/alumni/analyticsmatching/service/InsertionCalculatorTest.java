package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.uasz.alumni.analyticsmatching.service.InsertionCalculator.GroupStat;
import com.uasz.alumni.analyticsmatching.service.InsertionCalculator.Result;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Le taux doit être exact sur un jeu de test connu (ALUM-183). */
class InsertionCalculatorTest {

    private static final MonthDay REF = MonthDay.of(7, 1);

    private static GraduateEmployment g(int year, String specialty, boolean employed, LocalDate... starts) {
        return new GraduateEmployment(year, specialty, employed, List.of(starts));
    }

    private static List<GraduateEmployment> jeuConnu() {
        return List.of(
                g(2024, "Génie Logiciel", true, LocalDate.of(2024, 9, 15)),     // 2 mois
                g(2024, "Génie Logiciel", true, LocalDate.of(2024, 10, 1)),     // 3 mois
                g(2024, "Génie Logiciel", false),
                g(2024, "génie  logiciel ", true, LocalDate.of(2025, 1, 1)),    // 6 mois, même spécialité
                g(2023, "Réseaux", true, LocalDate.of(2023, 3, 1)),             // emploi antérieur au diplôme : pas de délai
                g(2023, "Réseaux", false, LocalDate.of(2023, 8, 1)),            // 1 mois, emploi terminé
                g(2023, "Cybersécurité", false));
    }

    @Test
    void calculeLesTauxExactsSurLeJeuConnu() {
        Result result = InsertionCalculator.compute(jeuConnu(), REF);

        assertThat(result.overallInsertionRate()).isEqualTo(57.1f); // 4 / 7
        assertThat(result.groups())
                .extracting(GroupStat::graduationYear, GroupStat::specialty, GroupStat::totalGraduates,
                        GroupStat::employedCount, GroupStat::insertionRate, GroupStat::averageMonthsToFirstJob)
                .containsExactly(
                        tuple(2024, "Génie Logiciel", 4, 3, 75.0f, 3.7f),
                        tuple(2023, "Cybersécurité", 1, 0, 0.0f, null),
                        tuple(2023, "Réseaux", 2, 1, 50.0f, 1.0f));
    }

    @Test
    void lOrdreDesDonneesNeChangePasLeResultat() {
        List<GraduateEmployment> inverse = new ArrayList<>(jeuConnu());
        Collections.reverse(inverse);

        assertThat(InsertionCalculator.compute(inverse, REF)).isEqualTo(InsertionCalculator.compute(jeuConnu(), REF));
    }

    @Test
    void leTauxGlobalEstCeluiDesEffectifsEtNonLaMoyenneDesTaux() {
        List<GraduateEmployment> data = new ArrayList<>();
        data.add(g(2023, "Réseaux", true));                  // promo A : 1 / 1 = 100 %
        data.add(g(2024, "Réseaux", true));                  // promo B : 1 / 9 = 11,1 %
        for (int i = 0; i < 8; i++) {
            data.add(g(2024, "Réseaux", false));
        }

        Result result = InsertionCalculator.compute(data, REF);

        assertThat(result.overallInsertionRate()).isEqualTo(20.0f); // 2 / 10, pas (100 + 11,1) / 2
        assertThat(result.groups()).extracting(GroupStat::insertionRate).containsExactly(11.1f, 100.0f);
    }

    @Test
    void sansDiplomeLeRapportEstVideEtLeTauxGlobalVautZero() {
        Result result = InsertionCalculator.compute(List.of(), REF);

        assertThat(result.groups()).isEmpty();
        assertThat(result.overallInsertionRate()).isZero();
    }

    @Test
    void leDelaiPartDeLaPremiereExperienceDebuteeApresLeDiplome() {
        // l'expérience de 2023 précède le diplôme : ignorée ; la première retenue est le 15/09/2024 (2 mois)
        Result result = InsertionCalculator.compute(List.of(
                g(2024, "Réseaux", true, LocalDate.of(2025, 1, 1), LocalDate.of(2023, 5, 1), LocalDate.of(2024, 9, 15))), REF);

        assertThat(result.groups().getFirst().averageMonthsToFirstJob()).isEqualTo(2.0f);
    }

    @Test
    void unDiplomeSansExperienceApresLeDiplomeEstIgnoreDeLaMoyenneDesDelais() {
        Result result = InsertionCalculator.compute(List.of(
                g(2024, "Réseaux", true, LocalDate.of(2024, 9, 1)),   // 2 mois
                g(2024, "Réseaux", true, LocalDate.of(2024, 1, 1))),  // avant le diplôme : pas de délai
                REF);

        assertThat(result.groups().getFirst().averageMonthsToFirstJob()).isEqualTo(2.0f);
        assertThat(result.groups().getFirst().employedCount()).isEqualTo(2);
    }

    @Test
    void ignoreLesSpecialitesVidesOuAbsentes() {
        Result result = InsertionCalculator.compute(List.of(g(2024, "  ", true), g(2024, null, true)), REF);

        assertThat(result.groups()).isEmpty();
        assertThat(result.overallInsertionRate()).isZero();
    }
}
