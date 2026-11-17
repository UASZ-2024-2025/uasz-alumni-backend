package com.uasz.alumni.analyticsmatching.controller;

import com.uasz.alumni.analyticsmatching.exceptions.NotImplementedYetException;
import com.uasz.alumni.analyticsmatching.spi.api.MatchingsApi;
import com.uasz.alumni.analyticsmatching.spi.model.CandidateMatchResult;
import com.uasz.alumni.analyticsmatching.spi.model.OfferMatchResult;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchingsController implements MatchingsApi {

    @Override
    public ResponseEntity<List<OfferMatchResult>> getMatchingOffersForStudent(UUID studentId, Integer minScore) {
        // TODO : offres recommandées pour l'étudiant (score >= minScore) -> 200 / 404
        throw new NotImplementedYetException("getMatchingOffersForStudent");
    }

    @Override
    public ResponseEntity<List<CandidateMatchResult>> getMatchingCandidatesForOffer(UUID offerId, Integer minScore) {
        // TODO : candidats recommandés pour l'offre (score >= minScore) -> 200 / 404
        throw new NotImplementedYetException("getMatchingCandidatesForOffer");
    }
}
