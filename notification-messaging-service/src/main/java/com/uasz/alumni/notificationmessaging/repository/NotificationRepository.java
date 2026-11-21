package com.uasz.alumni.notificationmessaging.repository;

import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
}
