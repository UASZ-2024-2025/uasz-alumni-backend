package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.analyticsmatching.spi.model.CandidateMatchResult;
import com.uasz.alumni.analyticsmatching.spi.model.OfferMatchResult;
import java.util.List;
import java.util.UUID;

public interface MatchingService {

    /**
     * Calcule et retourne les offres OPEN correspondant au profil d'un étudiant,
     * filtrées par un score minimum et triées par score décroissant.
     *
     * @param studentId identifiant unique de l'étudiant
     * @param minScore score minimal d'adéquation en pourcentage (0-100, par défaut 50)
     * @return liste des offres recommandées avec détails des compétences
     */
    List<OfferMatchResult> getMatchingOffersForStudent(UUID studentId, Integer minScore);

    /**
     * Recommande les candidats étudiants les plus qualifiés pour une offre donnée.
     * Ne retient que les étudiants disponibles, triés par score décroissant.
     * Accès réservé à l'auteur de l'offre et à l'administrateur.
     *
     * @param offerId identifiant unique de l'offre
     * @param minScore score minimal d'adéquation en pourcentage (0-100, par défaut 60)
     * @return liste des candidats qualifiés avec leur score
     */
    List<CandidateMatchResult> getMatchingCandidatesForOffer(UUID offerId, Integer minScore);
}
