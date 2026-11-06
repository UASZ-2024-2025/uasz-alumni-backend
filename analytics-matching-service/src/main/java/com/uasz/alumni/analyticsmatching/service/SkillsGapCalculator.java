package com.uasz.alumni.analyticsmatching.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Calcul pur (sans Spring) de l'écart de compétences.
 *
 * <ul>
 *   <li>demande = % d'offres qui exigent la compétence ;</li>
 *   <li>offre   = % d'étudiants/alumni qui la maîtrisent ;</li>
 *   <li>écart   = demande - offre, trié par ordre décroissant.</li>
 * </ul>
 * Seules les compétences demandées par au moins une offre sont analysées.
 */
public final class SkillsGapCalculator {

    /** Résultat intermédiaire, indépendant des classes générées du SPI. */
    public record SkillStat(String skillName, float demandPercentage, float supplyPercentage, float gap) { }

    private SkillsGapCalculator() { }

    public static List<SkillStat> compute(Collection<? extends Collection<String>> offers,
                                          Collection<? extends Collection<String>> profiles) {
        Map<String, String> displayNames = new LinkedHashMap<>();
        Map<String, Integer> offerCounts = countBySkill(offers, displayNames);
        Map<String, Integer> profileCounts = countBySkill(profiles, displayNames);

        List<SkillStat> stats = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : offerCounts.entrySet()) {
            double demand = percentage(entry.getValue(), offers.size());
            double supply = percentage(profileCounts.getOrDefault(entry.getKey(), 0), profiles.size());
            stats.add(new SkillStat(displayNames.get(entry.getKey()),
                    round1(demand), round1(supply), round1(demand - supply)));
        }
        stats.sort(Comparator.comparingDouble(SkillStat::gap).reversed()
                .thenComparing(Comparator.comparingDouble(SkillStat::demandPercentage).reversed())
                .thenComparing(SkillStat::skillName, String.CASE_INSENSITIVE_ORDER));
        return stats;
    }

    /** Les {@code limit} compétences les plus demandées dans les offres. */
    public static List<String> topRequired(List<SkillStat> stats, int limit) {
        return stats.stream()
                .sorted(Comparator.comparingDouble(SkillStat::demandPercentage).reversed()
                        .thenComparing(SkillStat::skillName, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(SkillStat::skillName)
                .toList();
    }

    private static Map<String, Integer> countBySkill(Collection<? extends Collection<String>> groups,
                                                     Map<String, String> displayNames) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Collection<String> skills : groups) {
            Set<String> seen = new HashSet<>(); // une compétence compte une seule fois par offre/profil
            for (String raw : skills) {
                String key = normalize(raw);
                if (key.isEmpty() || !seen.add(key)) {
                    continue;
                }
                displayNames.putIfAbsent(key, raw.strip());
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static double percentage(int count, int total) {
        return total == 0 ? 0.0 : (count * 100.0) / total;
    }

    private static float round1(double value) {
        return Math.round(value * 10.0) / 10.0f;
    }
}
