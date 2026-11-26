package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import org.springframework.http.HttpStatus;

/** Règles communes aux deux sens du matching (ALUM-180, ALUM-181) : seuil, conversions vers le calcul, erreurs. */
final class MatchingSupport {

    private MatchingSupport() { }

    /** {@code minScore} absent : valeur par défaut du contrat ; hors de 0..100 : 400. */
    static int threshold(Integer minScore, int defaultValue) {
        if (minScore == null) {
            return defaultValue;
        }
        if (minScore < 0 || minScore > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MIN_SCORE", "minScore doit être compris entre 0 et 100");
        }
        return minScore;
    }

    static MatchScoreCalculator.StudentProfileData profileData(StudentProfile student) {
        return new MatchScoreCalculator.StudentProfileData(student.getSkills(), student.getInterests(),
                student.getAvailabilityStatus() == null ? null : student.getAvailabilityStatus().getValue());
    }

    static MatchScoreCalculator.OfferData offerData(JobOffer offer) {
        return new MatchScoreCalculator.OfferData(offer.getRequiredSkills(), offer.getTitle(), offer.getDescription(),
                offer.getType() == null ? null : offer.getType().getValue());
    }

    static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    static ApiException upstreamUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                "Profils ou offres momentanément indisponibles, réessayez plus tard");
    }
}
