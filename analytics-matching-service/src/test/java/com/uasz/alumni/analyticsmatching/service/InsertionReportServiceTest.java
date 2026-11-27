package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.spi.model.InsertionReportItem;
import com.uasz.alumni.analyticsmatching.spi.model.InsertionReportResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** InsertionReportService sans Spring : la source de données est une fausse implémentation en mémoire. */
class InsertionReportServiceTest {

    /** Fausse source : mémorise les filtres reçus et renvoie les diplômés fournis. */
    private static final class FakeSource implements InsertionDataSource {
        final List<GraduateEmployment> data;
        final List<Object[]> calls = new ArrayList<>();

        FakeSource(List<GraduateEmployment> data) {
            this.data = data;
        }

        @Override
        public List<GraduateEmployment> graduates(Integer graduationYear, String specialty) {
            calls.add(new Object[] {graduationYear, specialty});
            return data;
        }
    }

    private static List<GraduateEmployment> jeuConnu() {
        return List.of(
                new GraduateEmployment(2024, "Génie Logiciel", true, List.of(LocalDate.of(2024, 9, 15))),
                new GraduateEmployment(2024, "Génie Logiciel", true, List.of(LocalDate.of(2024, 10, 1))),
                new GraduateEmployment(2024, "Génie Logiciel", false, List.of()),
                new GraduateEmployment(2024, "génie logiciel", true, List.of(LocalDate.of(2025, 1, 1))),
                new GraduateEmployment(2023, "Réseaux", true, List.of(LocalDate.of(2023, 3, 1))),
                new GraduateEmployment(2023, "Réseaux", false, List.of(LocalDate.of(2023, 8, 1))),
                new GraduateEmployment(2023, "Cybersécurité", false, List.of()));
    }

    @Test
    void produitLeRapportDuContratSurLeJeuConnu() {
        InsertionReportResponse response = new InsertionReportService(new FakeSource(jeuConnu())).buildReport(null, null);

        assertThat(response.getOverallInsertionRate()).isEqualTo(57.1f);
        assertThat(response.getReportData())
                .extracting(InsertionReportItem::getGraduationYear, InsertionReportItem::getSpecialty,
                        InsertionReportItem::getTotalGraduates, InsertionReportItem::getEmployedCount,
                        InsertionReportItem::getInsertionRate, InsertionReportItem::getAverageMonthsToFirstJob)
                .containsExactly(
                        tuple(2024, "Génie Logiciel", 4, 3, 75.0f, 3.7f),
                        tuple(2023, "Cybersécurité", 1, 0, 0.0f, null),
                        tuple(2023, "Réseaux", 2, 1, 50.0f, 1.0f));
    }

    @Test
    void transmetLesFiltresNettoyesALaSource() {
        FakeSource source = new FakeSource(List.of());

        new InsertionReportService(source).buildReport(2024, "  Génie Logiciel ");
        new InsertionReportService(source).buildReport(null, "   ");

        assertThat(source.calls.get(0)).containsExactly(2024, "Génie Logiciel");
        assertThat(source.calls.get(1)).containsExactly(null, null);
    }

    @Test
    void sansDiplomeRenvoieUnRapportVideEtUnTauxGlobalNul() {
        InsertionReportResponse response = new InsertionReportService(new FakeSource(List.of())).buildReport(2030, null);

        assertThat(response.getReportData()).isEmpty();
        assertThat(response.getOverallInsertionRate()).isZero();
    }

    @Test
    void refuseUneAnneeHorsBornesSansInterrogerLaSource() {
        FakeSource source = new FakeSource(jeuConnu());
        InsertionReportService service = new InsertionReportService(source);

        for (int annee : new int[] {1989, 2101, -1, 0}) {
            assertThatThrownBy(() -> service.buildReport(annee, null))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getCode()).isEqualTo("INVALID_GRADUATION_YEAR");
                    });
        }
        assertThat(source.calls).isEmpty();
    }

    @Test
    void accepteLesBornesDeLAnnee() {
        InsertionReportService service = new InsertionReportService(new FakeSource(List.of()));

        assertThat(service.buildReport(1990, null).getReportData()).isEmpty();
        assertThat(service.buildReport(2100, null).getReportData()).isEmpty();
    }

    @Test
    void refuseUneSpecialiteTropLongue() {
        InsertionReportService service = new InsertionReportService(new FakeSource(List.of()));

        assertThatThrownBy(() -> service.buildReport(null, "x".repeat(151)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("INVALID_SPECIALTY");
                });
    }
}
