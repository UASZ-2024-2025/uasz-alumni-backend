package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import com.uasz.alumni.notificationmessaging.repository.NotificationRepository;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /**
     * Enregistre une notification émise par un service ou un administrateur. Seul le canal IN_APP est
     * délivré pour l'instant (consultée via GET /notifications) ; EMAIL et PUSH sont stockés.
     */
    @Transactional
    public NotificationEntity send(SendNotificationRequest req, String emittedBy) {
        NotificationChannel channel = req.getChannel() != null ? req.getChannel() : NotificationChannel.IN_APP;
        // TODO : délivrance EMAIL (SMTP) et PUSH
        NotificationEntity saved = notifications.save(new NotificationEntity(req.getUserId(), req.getType(), channel,
                req.getTitle(), req.getContent(), req.getReferenceId(), emittedBy));
        log.info("Notification {} ({}) créée pour l'utilisateur {} par {}", saved.getId(), saved.getType(),
                saved.getUserId(), emittedBy);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<NotificationEntity> list(UUID userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return unreadOnly
                ? notifications.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    /** 404 aussi pour la notification d'un autre utilisateur : on ne révèle pas son existence. */
    @Transactional
    public NotificationEntity markAsRead(UUID userId, UUID notificationId) {
        NotificationEntity n = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                        "Notification introuvable : " + notificationId));
        n.markAsRead();
        return n;
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notifications.markAllAsRead(userId);
    }
}
