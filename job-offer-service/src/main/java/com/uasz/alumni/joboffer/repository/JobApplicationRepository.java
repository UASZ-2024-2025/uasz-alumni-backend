package com.uasz.alumni.joboffer.repository;

import com.uasz.alumni.joboffer.model.JobApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    /** Candidatures d'une offre, les plus récentes d'abord. */
    @EntityGraph(attributePaths = "offer")
    List<JobApplication> findByOfferIdOrderByCreatedAtDesc(UUID offerId);

    /** Charge l'offre avec la candidature : nécessaire pour vérifier l'auteur sans requête supplémentaire. */
    @EntityGraph(attributePaths = "offer")
    Optional<JobApplication> findWithOfferById(UUID id);
}
