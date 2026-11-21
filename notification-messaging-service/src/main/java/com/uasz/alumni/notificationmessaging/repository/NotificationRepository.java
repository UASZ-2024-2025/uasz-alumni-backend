package com.uasz.alumni.notificationmessaging.repository;

import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    // Centre de notifications (ALUM-139) : chaque utilisateur ne voit que les siennes.
    Page<NotificationEntity> findByUserId(UUID userId, Pageable pageable);

    Page<NotificationEntity> findByUserIdAndReadFalse(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    /** « Tout marquer comme lu » en une requête ; la date de lecture des notifications déjà lues est conservée. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationEntity n set n.read = true, n.readAt = :now where n.userId = :userId and n.read = false")
    int markAllRead(UUID userId, OffsetDateTime now);
}
