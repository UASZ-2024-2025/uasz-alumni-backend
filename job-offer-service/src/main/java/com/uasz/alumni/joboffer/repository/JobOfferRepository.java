package com.uasz.alumni.joboffer.repository;

import com.uasz.alumni.joboffer.model.JobOfferEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobOfferRepository extends JpaRepository<JobOfferEntity, UUID> {

    /** Charge aussi les compétences (open-in-view désactivé : le mapping se fait hors transaction). */
    @EntityGraph(attributePaths = "requiredSkills")
    Optional<JobOfferEntity> findWithSkillsById(UUID id);
}
