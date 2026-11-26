package com.uasz.alumni.analyticsmatching.controller;

import com.uasz.alumni.analyticsmatching.exceptions.NotImplementedYetException;
import com.uasz.alumni.analyticsmatching.security.AuthenticatedUser;
import com.uasz.alumni.analyticsmatching.service.OfferMatchingService;
import com.uasz.alumni.analyticsmatching.spi.api.MatchingsApi;
import com.uasz.alumni.analyticsmatching.spi.model.CandidateMatchResult;
import com.uasz.alumni.analyticsmatching.spi.model.OfferMatchResult;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchingsController implements MatchingsApi {

    private final OfferMatchingService offerMatching;

    public MatchingsController(OfferMatchingService offerMatching) {
        this.offerMatching = offerMatching;
    }

    @Override
    public ResponseEntity<List<OfferMatchResult>> getMatchingOffersForStudent(UUID studentId, Integer minScore) {
        return ResponseEntity.ok(offerMatching.matchingOffers(studentId, minScore, currentUser()));
    }

    @Override
    public ResponseEntity<List<CandidateMatchResult>> getMatchingCandidatesForOffer(UUID offerId, Integer minScore) {
        // TODO : candidats recommandés pour l'offre (ALUM-181)
        throw new NotImplementedYetException("getMatchingCandidatesForOffer");
    }

    private static AuthenticatedUser currentUser() {
        return (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
