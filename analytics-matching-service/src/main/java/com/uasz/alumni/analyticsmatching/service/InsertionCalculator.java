package com.uasz.alumni.analyticsmatching.service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Calcul pur (sans Spring) du taux d'insertion professionnelle.
 *
 * <ul>
 *   <li>une ligne par promotion (année de diplôme) et spécialité (casse et espaces de bord ignorés) ;</li>
 *   <li>taux = diplômés en emploi / diplômés x 100 ; taux global = total des en emploi / total des diplômés
 *       (et non la moyenne des taux) ;</li>
 *   <li>délai = mois entiers entre la date de référence de la promotion et le début de la première expérience
 *       débutée à cette date ou après ; les diplômés sans telle expérience sont ignorés de la moyenne.</li>
 * </ul>
 * Tous les taux et délais sont arrondis à une décimale.
 */
public final class InsertionCalculator {

    /** Ligne du rapport, indépendante des classes générées du SPI. */
    public record GroupStat(int graduationYear, String specialty, int totalGraduates, int employedCount,
                            float insertionRate, Float averageMonthsToFirstJob) { }

    public record Result(float overallInsertionRate, List<GroupStat> groups) { }

    private record Key(int year, String specialty) { }

    private static final class Accumulator {
        String displayName;
        int total;
        int employed;
        long delayMonths;
        int delayCount;
    }

    private InsertionCalculator() { }

    public static Result compute(Collection<GraduateEmployment> graduates, MonthDay graduationReference) {
        Map<Key, Accumulator> groups = new HashMap<>();
        for (GraduateEmployment graduate : graduates) {
            String specialty = clean(graduate == null ? null : graduate.specialty());
            if (specialty.isEmpty()) {
                continue;
            }
            Accumulator acc = groups.computeIfAbsent(
                    new Key(graduate.graduationYear(), specialty.toLowerCase(Locale.ROOT)), k -> new Accumulator());
            // casse variable d'une même spécialité : même graphie quel que soit l'ordre d'arrivée
            if (acc.displayName == null || specialty.compareTo(acc.displayName) < 0) {
                acc.displayName = specialty;
            }
            acc.total++;
            if (graduate.employed()) {
                acc.employed++;
            }
            Optional<Long> delay = monthsToFirstJob(graduate, graduationReference);
            if (delay.isPresent()) {
                acc.delayMonths += delay.get();
                acc.delayCount++;
            }
        }

        List<GroupStat> stats = new ArrayList<>();
        int totalGraduates = 0;
        int totalEmployed = 0;
        for (Map.Entry<Key, Accumulator> entry : groups.entrySet()) {
            Accumulator acc = entry.getValue();
            totalGraduates += acc.total;
            totalEmployed += acc.employed;
            stats.add(new GroupStat(entry.getKey().year(), acc.displayName, acc.total, acc.employed,
                    round1(percentage(acc.employed, acc.total)),
                    acc.delayCount == 0 ? null : round1((double) acc.delayMonths / acc.delayCount)));
        }
        stats.sort(Comparator.comparingInt(GroupStat::graduationYear).reversed()
                .thenComparing(GroupStat::specialty, String.CASE_INSENSITIVE_ORDER));
        return new Result(round1(percentage(totalEmployed, totalGraduates)), stats);
    }

    private static Optional<Long> monthsToFirstJob(GraduateEmployment graduate, MonthDay graduationReference) {
        LocalDate reference = graduationReference.atYear(graduate.graduationYear());
        return graduate.experienceStartDates().stream()
                .filter(Objects::nonNull)
                .filter(start -> !start.isBefore(reference))
                .min(Comparator.naturalOrder())
                .map(start -> ChronoUnit.MONTHS.between(reference, start));
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
    }

    private static double percentage(int count, int total) {
        return total == 0 ? 0.0 : (count * 100.0) / total;
    }

    private static float round1(double value) {
        return Math.round(value * 10.0) / 10.0f;
    }
}
