package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import com.uasz.alumni.analyticsmatching.client.JobOfferServiceClient;
import com.uasz.alumni.analyticsmatching.client.StudentServiceClient;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.spi.model.OfferMatchResult;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MatchingServiceImpl implements MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingServiceImpl.class);

    private final StudentServiceClient studentServiceClient;
    private final JobOfferServiceClient jobOfferServiceClient;

    public MatchingServiceImpl(
            StudentServiceClient studentServiceClient,
            JobOfferServiceClient jobOfferServiceClient) {
        this.studentServiceClient = studentServiceClient;
        this.jobOfferServiceClient = jobOfferServiceClient;
    }

    @Override
    public List<OfferMatchResult> getMatchingOffersForStudent(UUID studentId, Integer minScore) {
        if (studentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                    "L'identifiant de l'étudiant est obligatoire.");
        }

        // Seuil d'adéquation minimal (50% par défaut)
        int threshold = (minScore != null && minScore >= 0 && minScore <= 100) ? minScore : 50;

        // 1. Récupération du profil étudiant depuis alumni-student-company-service
        StudentProfile student = studentServiceClient.getStudentProfile(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND",
                        "Étudiant introuvable avec l'identifiant : " + studentId));

        // Normalisation des compétences de l'étudiant (minuscules, sans espaces superflus)
        Set<String> normalizedStudentSkills = (student.getSkills() != null)
                ? student.getSkills().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet())
                : Collections.emptySet();

        // 2. Récupération des offres depuis job-offer-service
        List<JobOffer> rawOffers = jobOfferServiceClient.getJobOffers(OfferStatus.OPEN);

        // Règle métier : Ne prendre QUE les offres au statut OPEN
        List<JobOffer> openOffers = (rawOffers != null)
                ? rawOffers.stream()
                        .filter(offer -> offer != null && offer.getStatus() == OfferStatus.OPEN)
                        .toList()
                : Collections.emptyList();

        List<OfferMatchResult> matchingResults = new ArrayList<>();

        // 3. Calcul du score d'adéquation pour chaque offre OPEN
        for (JobOffer offer : openOffers) {
            List<String> requiredSkills = offer.getRequiredSkills() != null
                    ? offer.getRequiredSkills().stream().filter(s -> s != null && !s.isBlank()).toList()
                    : Collections.emptyList();

            int score;
            List<String> matchingSkills = new ArrayList<>();
            List<String> missingSkills = new ArrayList<>();

            if (requiredSkills.isEmpty()) {
                // Si aucune compétence n'est explicitement exigée, l'offre correspond à 100%
                score = 100;
            } else {
                for (String reqSkill : requiredSkills) {
                    String normReq = reqSkill.trim().toLowerCase();
                    if (normalizedStudentSkills.contains(normReq)) {
                        matchingSkills.add(reqSkill);
                    } else {
                        missingSkills.add(reqSkill);
                    }
                }
                score = (int) Math.round(((double) matchingSkills.size() / requiredSkills.size()) * 100.0);
            }

            // Critère d'acceptation : seules les offres au-dessus de minScore sont renvoyées
            if (score >= threshold) {
                OfferMatchResult result = new OfferMatchResult();
                result.setOfferId(offer.getId());
                result.setOfferTitle(offer.getTitle());
                result.setCompanyName(offer.getCompanyName());
                result.setMatchScore(score);
                result.setMatchingSkills(matchingSkills);
                result.setMissingSkills(missingSkills);

                matchingResults.add(result);
            }
        }

        // Règle métier : Tri des résultats par score décroissant
        matchingResults.sort(Comparator.comparing(OfferMatchResult::getMatchScore).reversed()
                .thenComparing(OfferMatchResult::getOfferTitle, Comparator.nullsLast(Comparator.naturalOrder())));

        log.info("Matching calculé pour l'étudiant {} : {} offres retenues avec un score >= {}%",
                studentId, matchingResults.size(), threshold);

        return matchingResults;
    }
}
