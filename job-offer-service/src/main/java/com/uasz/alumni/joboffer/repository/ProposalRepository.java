package com.uasz.alumni.joboffer.repository;

import com.uasz.alumni.joboffer.model.ProposalEntity;
import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProposalRepository extends JpaRepository<ProposalEntity, UUID> {

    @EntityGraph(attributePaths = "offer")
    Optional<ProposalEntity> findWithOfferById(UUID id);

    boolean existsByOfferIdAndStudentIdAndStatus(UUID offerId, UUID studentId, ProposalStatus status);

    @Query("""
            SELECT p FROM ProposalEntity p
            JOIN FETCH p.offer
            WHERE (:studentId IS NULL OR p.studentId = :studentId)
              AND (:recruiterId IS NULL OR p.recruiterId = :recruiterId)
              AND (:status IS NULL OR p.status = :status)
            ORDER BY p.createdAt DESC
            """)
    List<ProposalEntity> search(@Param("studentId") UUID studentId,
                                @Param("recruiterId") UUID recruiterId,
                                @Param("status") ProposalStatus status);

    @Modifying
    @Query("""
            UPDATE ProposalEntity p
            SET p.status = :expired, p.updatedAt = :now
            WHERE p.status = :pending AND p.expiresAt <= :now
            """)
    int expirePendingProposals(@Param("pending") ProposalStatus pending,
                               @Param("expired") ProposalStatus expired,
                               @Param("now") OffsetDateTime now);
}
