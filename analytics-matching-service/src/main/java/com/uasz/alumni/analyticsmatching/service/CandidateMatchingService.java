package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.AvailabilityStatus;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import com.uasz.alumni.analyticsmatching.client.JobOffersClient;
import com.uasz.alumni.analyticsmatching.client.StudentProfilesClient;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.security.AuthenticatedUser;
import com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.MatchScore;
import com.uasz.alumni.analyticsmatching.spi.model.CandidateMatchResult;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Étudiants recommandés pour une offre (ALUM-181) : étudiants disponibles dont le score (ALUM-179,
 * {@link MatchScoreCalculator}) atteint {@code minScore}, du meilleur au moins bon. Les données sont lues à chaque
 * appel auprès de job-offer-service et d'alumni-student-company-service, avec le JWT de l'appelant.
 *
 * <p>Accès : l'auteur de l'offre ou un administrateur. Un étudiant est disponible s'il a renseigné une
 * disponibilité autre que {@code NOT_AVAILABLE}.
 */
@Service
public class CandidateMatchingService {

    static final int DEFAULT_MIN_SCORE = 60;
    private static final String ADMIN = "ADMINISTRATEUR";

    private final JobOffersClient offers;
    private final StudentProfilesClient students;

    public CandidateMatchingService(JobOffersClient offers, StudentProfilesClient students) {
        this.offers = offers;
        this.students = students;
    }

    public List<CandidateMatchResult> matchingCandidates(UUID offerId, Integer minScore, AuthenticatedUser caller) {
        int threshold = MatchingSupport.threshold(minScore, DEFAULT_MIN_SCORE);
        try {
            JobOffer offer = offers.find(offerId).orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offre introuvable : " + offerId));
            if (!ADMIN.equals(caller.role()) && !caller.id().equals(offer.getPublisherId())) {
                throw MatchingSupport.forbidden("Réservé à l'auteur de l'offre et à l'administrateur");
            }
            MatchScoreCalculator.OfferData offerData = MatchingSupport.offerData(offer);
            return students.listAll().stream()
                    .filter(CandidateMatchingService::isAvailable)
                    .map(student -> toResult(student,
                            MatchScoreCalculator.compute(MatchingSupport.profileData(student), offerData)))
                    .filter(result -> result.getMatchScore() >= threshold)
                    .sorted(Comparator.comparing(CandidateMatchResult::getMatchScore, Comparator.reverseOrder())
                            .thenComparing(CandidateMatchResult::getStudentName, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        } catch (RestClientException e) {
            throw MatchingSupport.upstreamUnavailable();
        }
    }

    private static boolean isAvailable(StudentProfile student) {
        return student.getAvailabilityStatus() != null && student.getAvailabilityStatus() != AvailabilityStatus.NOT_AVAILABLE;
    }

    /** {@code studentId} = id utilisateur : celui que prennent les propositions et la messagerie. */
    private static CandidateMatchResult toResult(StudentProfile student, MatchScore score) {
        String name = Stream.of(student.getFirstName(), student.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::strip)
                .collect(Collectors.joining(" "));
        return new CandidateMatchResult()
                .studentId(student.getUserId() != null ? student.getUserId() : student.getId())
                .studentName(name.isEmpty() ? null : name)
                .specialty(student.getSpecialty())
                .matchScore(score.matchScore())
                .matchingSkills(score.matchingSkills());
    }
}
