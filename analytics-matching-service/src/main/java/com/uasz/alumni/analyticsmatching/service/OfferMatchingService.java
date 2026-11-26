package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import com.uasz.alumni.analyticsmatching.client.JobOffersClient;
import com.uasz.alumni.analyticsmatching.client.StudentProfilesClient;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.security.AuthenticatedUser;
import com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.MatchScore;
import com.uasz.alumni.analyticsmatching.spi.model.OfferMatchResult;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Offres recommandées à un étudiant (ALUM-180) : offres ouvertes dont le score (ALUM-179, {@link MatchScoreCalculator})
 * atteint {@code minScore}, de la meilleure à la moins bonne. Les données sont lues à chaque appel auprès
 * d'alumni-student-company-service et de job-offer-service, avec le JWT de l'appelant.
 *
 * <p>Accès : l'étudiant pour lui-même, ou un administrateur.
 */
@Service
public class OfferMatchingService {

    static final int DEFAULT_MIN_SCORE = 50;
    private static final String STUDENT = "ETUDIANT";
    private static final String ADMIN = "ADMINISTRATEUR";

    private final StudentProfilesClient students;
    private final JobOffersClient offers;

    public OfferMatchingService(StudentProfilesClient students, JobOffersClient offers) {
        this.students = students;
        this.offers = offers;
    }

    public List<OfferMatchResult> matchingOffers(UUID studentId, Integer minScore, AuthenticatedUser caller) {
        int threshold = MatchingSupport.threshold(minScore, DEFAULT_MIN_SCORE);
        boolean admin = ADMIN.equals(caller.role());
        if (!admin && !STUDENT.equals(caller.role())) {
            throw MatchingSupport.forbidden("Réservé à l'étudiant concerné et à l'administrateur");
        }
        try {
            StudentProfile student = students.find(studentId).orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Étudiant introuvable : " + studentId));
            if (!admin && !caller.id().equals(student.getUserId())) {
                throw MatchingSupport.forbidden("Un étudiant ne consulte que ses propres recommandations");
            }
            MatchScoreCalculator.StudentProfileData profile = MatchingSupport.profileData(student);
            return offers.listOpen().stream()
                    .map(offer -> toResult(offer, MatchScoreCalculator.compute(profile, MatchingSupport.offerData(offer))))
                    .filter(result -> result.getMatchScore() >= threshold)
                    .sorted(Comparator.comparing(OfferMatchResult::getMatchScore, Comparator.reverseOrder())
                            .thenComparing(OfferMatchResult::getOfferTitle, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        } catch (RestClientException e) {
            throw MatchingSupport.upstreamUnavailable();
        }
    }

    private static OfferMatchResult toResult(JobOffer offer, MatchScore score) {
        return new OfferMatchResult()
                .offerId(offer.getId())
                .offerTitle(offer.getTitle())
                .companyName(offer.getCompanyName())
                .matchScore(score.matchScore())
                .matchingSkills(score.matchingSkills())
                .missingSkills(score.missingSkills());
    }
}
