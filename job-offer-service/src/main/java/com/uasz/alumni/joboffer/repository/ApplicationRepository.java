package com.uasz.alumni.joboffer.repository;

import com.uasz.alumni.joboffer.model.ApplicationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    boolean existsByOfferIdAndStudentUserId(UUID offerId, UUID studentUserId);

    @EntityGraph(attributePaths = "offer")
    List<ApplicationEntity> findByOfferIdOrderByCreatedAtAsc(UUID offerId);

    @EntityGraph(attributePaths = "offer")
    Optional<ApplicationEntity> findWithOfferById(UUID id);
}
