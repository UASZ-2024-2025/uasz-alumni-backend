package com.uasz.alumni.notificationmessaging.repository;

import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<NotificationEntity> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update NotificationEntity n set n.read = true where n.userId = :userId and n.read = false")
    int markAllAsRead(UUID userId);
}
