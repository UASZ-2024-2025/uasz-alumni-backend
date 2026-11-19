package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AlumniRepository extends JpaRepository<Alumni, UUID> {

    /** Un seul profil par compte (user_id unique). */
    Optional<Alumni> findByUserId(UUID userId);

    /**
     * Verrouille le profil (SELECT ... FOR UPDATE) : deux ajouts simultanés d'une expérience courante
     * pour le même Alumni sont ainsi traités l'un après l'autre.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Alumni a where a.id = :id")
    Optional<Alumni> findByIdForUpdate(UUID id);

    /** Diplômés d'une promotion (ALUM-88) : même année de diplôme et même spécialité, sans tenir compte de la casse. */
    List<Alumni> findByGraduationYearAndSpecialtyIgnoreCaseOrderByLastNameAscFirstNameAsc(Integer graduationYear,
                                                                                           String specialty);

    /** Nombre de diplômés d'une promotion : même règle que la liste. */
    long countByGraduationYearAndSpecialtyIgnoreCase(Integer graduationYear, String specialty);
}
