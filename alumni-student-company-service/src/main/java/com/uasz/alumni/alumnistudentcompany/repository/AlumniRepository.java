package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AlumniRepository extends JpaRepository<Alumni, UUID> {

    /**
     * Verrouille le profil (SELECT ... FOR UPDATE) : deux ajouts simultanés d'une expérience courante
     * pour le même Alumni sont ainsi traités l'un après l'autre.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Alumni a where a.id = :id")
    Optional<Alumni> findByIdForUpdate(UUID id);
}
