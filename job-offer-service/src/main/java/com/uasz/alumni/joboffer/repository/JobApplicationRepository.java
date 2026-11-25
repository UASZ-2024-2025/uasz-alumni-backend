package com.uasz.alumni.joboffer.repository;

import com.uasz.alumni.joboffer.model.JobApplication;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    /** Candidatures d'une offre, les plus récentes d'abord. */
    @EntityGraph(attributePaths = "offer")
    List<JobApplication> findByOfferIdOrderByCreatedAtDesc(UUID offerId);

    /** Charge l'offre avec la candidature : nécessaire pour vérifier l'auteur sans requête supplémentaire. */
    @EntityGraph(attributePaths = "offer")
    Optional<JobApplication> findWithOfferById(UUID id);

    /** Une offre qui a reçu des candidatures est archivée au lieu d'être supprimée (ALUM-148). */
    boolean existsByOfferId(UUID offerId);

    /** Un étudiant ne postule qu'une fois par offre (ALUM-172). */
    boolean existsByOfferIdAndStudentId(UUID offerId, UUID studentId);

    // Suivi des candidatures (ALUM-172) : l'offre est chargée avec la page (titre affiché).
    @EntityGraph(attributePaths = "offer")
    Page<JobApplication> findByStudentId(UUID studentId, Pageable pageable);

    @EntityGraph(attributePaths = "offer")
    Page<JobApplication> findByStudentIdAndStatus(UUID studentId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "offer")
    Page<JobApplication> findByStatus(ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "offer")
    Page<JobApplication> findAllBy(Pageable pageable);

    /** Nombre de candidatures d'un étudiant pour un statut. */
    interface StatusCount {
        ApplicationStatus getStatus();

        long getTotal();
    }

    /** Candidatures d'un étudiant regroupées par statut (les statuts sans candidature sont absents). */
    @Query("select a.status as status, count(a) as total from JobApplication a "
        + "where a.studentId = :studentId group by a.status")
    List<StatusCount> countByStatusForStudent(@Param("studentId") UUID studentId);
}
