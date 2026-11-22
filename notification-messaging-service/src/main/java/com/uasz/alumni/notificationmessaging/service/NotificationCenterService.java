package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import com.uasz.alumni.notificationmessaging.repository.NotificationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ALUM-139 : centre de notifications de l'utilisateur connecté. Chacun ne voit et ne marque que les siennes :
 * la notification d'un autre utilisateur répond 404, comme une notification inexistante.
 */
@Service
public class NotificationCenterService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationCenterService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Plus récentes d'abord. Le contrat ne déclare pas de 400 : une pagination hors bornes est ramenée dans
     * les bornes (page >= 0, 1 <= size <= 100) au lieu d'être refusée.
     */
    @Transactional(readOnly = true)
    public Page<NotificationEntity> list(UUID userId, Boolean unreadOnly, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? DEFAULT_SIZE : Math.clamp(size, 1, MAX_SIZE);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return Boolean.TRUE.equals(unreadOnly)
                ? notifications.findByUserIdAndReadFalse(userId, pageable)
                : notifications.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationEntity markRead(UUID id, UUID userId) {
        NotificationEntity notification = notifications.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification introuvable : " + id));
        notification.markRead(OffsetDateTime.now(clock));
        return notification;
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId, OffsetDateTime.now(clock));
    }
}
