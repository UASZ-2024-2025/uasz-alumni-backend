package com.uasz.alumni.analyticsmatching.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Score de correspondance entre un étudiant et une offre (ALUM-179), de 0 à 100.
 *
 * <p>Calcul pur (sans Spring ni appel réseau), commun aux deux sens du matching : offres pour un étudiant
 * (ALUM-180) et candidats pour une offre (ALUM-181). La formule est décrite dans {@code docs/matching.md} :
 * <pre>
 *   score = arrondi(70 × compétences + 20 × intérêts + 10 × disponibilité)
 * </pre>
 * chaque critère valant entre 0 et 1. Une information absente côté étudiant (aucun intérêt déclaré,
 * disponibilité non renseignée) vaut {@value #NEUTRAL} : elle ne favorise ni ne pénalise.
 */
public final class MatchScoreCalculator {

    static final int SKILLS_WEIGHT = 70;
    static final int INTERESTS_WEIGHT = 20;
    static final int AVAILABILITY_WEIGHT = 10;
    static final double NEUTRAL = 0.5;

    /** Nombre d'intérêts retrouvés dans l'offre pour obtenir le maximum sur ce critère. */
    static final int INTERESTS_FOR_FULL_SCORE = 2;
    /** Disponibilité pour l'autre type d'offre (stage au lieu d'emploi, ou l'inverse). */
    static final double OTHER_OFFER_TYPE = 0.25;
    /** En dessous de cette longueur, une clé n'est cherchée que comme mot entier (évite « go » dans « google »). */
    private static final int MIN_KEY_LENGTH_FOR_CONTAINS = 5;

    /** Valeurs du contrat alumni-student-company-service (AvailabilityStatus). */
    public static final String AVAILABLE_IMMEDIATELY = "AVAILABLE_IMMEDIATELY";
    public static final String AVAILABLE_FOR_INTERNSHIP = "AVAILABLE_FOR_INTERNSHIP";
    public static final String AVAILABLE_FOR_JOB = "AVAILABLE_FOR_JOB";
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    /** Valeurs du contrat job-offer-service (OfferType). */
    public static final String INTERNSHIP = "INTERNSHIP";
    public static final String JOB = "JOB";

    /**
     * Données de l'étudiant utiles au score, indépendantes des classes générées des SPIs.
     *
     * @param availabilityStatus valeur de {@code AvailabilityStatus}, ou {@code null} si non renseignée
     */
    public record StudentProfileData(Collection<String> skills, Collection<String> interests,
                                     String availabilityStatus) { }

    /**
     * Données de l'offre utiles au score.
     *
     * @param type valeur de {@code OfferType} ({@code INTERNSHIP} ou {@code JOB}), ou {@code null}
     */
    public record OfferData(Collection<String> requiredSkills, String title, String description, String type) { }

    /**
     * Résultat : score global, détail par critère (entre 0 et 1) et compétences de l'offre, avec le libellé
     * de l'offre, présentes ou absentes chez l'étudiant.
     */
    public record MatchScore(int matchScore, List<String> matchingSkills, List<String> missingSkills,
                             double skillsScore, double interestsScore, double availabilityScore) { }

    private MatchScoreCalculator() { }

    public static MatchScore compute(StudentProfileData student, OfferData offer) {
        Objects.requireNonNull(student, "student");
        Objects.requireNonNull(offer, "offer");

        Set<String> studentSkills = keys(student.skills());
        List<String> matching = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        // Une compétence exigée deux fois (« Java », « java ») ne compte qu'une fois ; on garde le premier libellé.
        for (Map.Entry<String, String> required : distinctByKey(offer.requiredSkills()).entrySet()) {
            (studentSkills.contains(required.getKey()) ? matching : missing).add(required.getValue());
        }
        int requiredCount = matching.size() + missing.size();
        // Offre sans compétence exigée : rien ne manque à l'étudiant.
        double skills = requiredCount == 0 ? 1.0 : (double) matching.size() / requiredCount;

        double interests = interestsScore(student.interests(), offer);
        double availability = availabilityScore(student.availabilityStatus(), offer.type());

        int score = (int) Math.round(SKILLS_WEIGHT * skills + INTERESTS_WEIGHT * interests
                + AVAILABILITY_WEIGHT * availability);
        return new MatchScore(score, List.copyOf(matching), List.copyOf(missing), skills, interests, availability);
    }

    /**
     * Part des intérêts de l'étudiant retrouvés dans l'offre (compétences exigées, titre, description).
     * {@value #INTERESTS_FOR_FULL_SCORE} intérêts retrouvés suffisent pour le maximum : un étudiant aux
     * intérêts nombreux n'est pas pénalisé.
     */
    static double interestsScore(Collection<String> interests, OfferData offer) {
        Map<String, String> distinct = distinctByKey(interests);
        if (distinct.isEmpty()) {
            return NEUTRAL;
        }
        Set<String> requiredKeys = keys(offer.requiredSkills());
        String offerText = " " + SkillNormalizer.text(join(offer.title(), offer.description())) + " ";
        String offerCompact = SkillNormalizer.key(offerText.replace(" ", ""));
        long found = distinct.entrySet().stream()
                .filter(e -> requiredKeys.contains(e.getKey())
                        || containsWord(offerText, SkillNormalizer.text(e.getValue()))
                        || (e.getKey().length() >= MIN_KEY_LENGTH_FOR_CONTAINS && offerCompact.contains(e.getKey())))
                .count();
        return Math.min(1.0, (double) found / Math.min(distinct.size(), INTERESTS_FOR_FULL_SCORE));
    }

    static double availabilityScore(String availabilityStatus, String offerType) {
        if (availabilityStatus == null || availabilityStatus.isBlank()) {
            return NEUTRAL;
        }
        return switch (availabilityStatus) {
            case AVAILABLE_IMMEDIATELY -> 1.0;
            case AVAILABLE_FOR_INTERNSHIP -> sameTypeOr(offerType, INTERNSHIP);
            case AVAILABLE_FOR_JOB -> sameTypeOr(offerType, JOB);
            case NOT_AVAILABLE -> 0.0;
            default -> NEUTRAL; // valeur ajoutée au contrat plus tard : ni bonus ni pénalité
        };
    }

    private static double sameTypeOr(String offerType, String wanted) {
        if (offerType == null || offerType.isBlank()) {
            return NEUTRAL;
        }
        return wanted.equals(offerType) ? 1.0 : OTHER_OFFER_TYPE;
    }

    private static boolean containsWord(String paddedText, String phrase) {
        return !phrase.isEmpty()
                && Pattern.compile("(?<![a-z0-9])" + Pattern.quote(phrase) + "(?![a-z0-9])").matcher(paddedText).find();
    }

    /** Clé normalisée → premier libellé d'origine, dans l'ordre ; libellés vides ignorés. */
    private static Map<String, String> distinctByKey(Collection<String> labels) {
        Map<String, String> byKey = new LinkedHashMap<>();
        if (labels != null) {
            for (String label : labels) {
                String key = SkillNormalizer.key(label);
                if (!key.isEmpty()) {
                    byKey.putIfAbsent(key, label.strip());
                }
            }
        }
        return byKey;
    }

    private static Set<String> keys(Collection<String> labels) {
        return distinctByKey(labels).keySet().stream().collect(Collectors.toUnmodifiableSet());
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null) {
                sb.append(part).append(' ');
            }
        }
        return sb.toString();
    }
}
