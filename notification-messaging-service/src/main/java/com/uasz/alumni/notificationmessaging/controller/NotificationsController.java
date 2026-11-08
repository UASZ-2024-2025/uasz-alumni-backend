package com.uasz.alumni.notificationmessaging.controller;

import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import com.uasz.alumni.notificationmessaging.security.CurrentUserProvider;
import com.uasz.alumni.notificationmessaging.service.NotificationService;
import com.uasz.alumni.notificationmessaging.spi.api.NotificationsApi;
import com.uasz.alumni.notificationmessaging.spi.model.Notification;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationPagedResponse;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import com.uasz.alumni.notificationmessaging.spi.model.UnreadCountResponse;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationsController implements NotificationsApi {

    private final NotificationService notifications;
    private final CurrentUserProvider currentUser;

    public NotificationsController(NotificationService notifications, CurrentUserProvider currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<NotificationPagedResponse> getUserNotifications(Boolean unreadOnly, Integer page, Integer size) {
        Page<NotificationEntity> result = notifications.list(currentUser.requireUserId(), Boolean.TRUE.equals(unreadOnly),
                page != null ? page : 0, size != null ? size : 20);
        NotificationPagedResponse body = new NotificationPagedResponse();
        body.setContent(result.getContent().stream().map(NotificationsController::toDto).toList());
        body.setTotalElements(Math.toIntExact(result.getTotalElements()));
        body.setPage(result.getNumber());
        body.setSize(result.getSize());
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<UnreadCountResponse> getUnreadNotificationCount() {
        UnreadCountResponse body = new UnreadCountResponse();
        body.setUnreadCount(Math.toIntExact(notifications.unreadCount(currentUser.requireUserId())));
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Notification> markNotificationAsRead(UUID id) {
        return ResponseEntity.ok(toDto(notifications.markAsRead(currentUser.requireUserId(), id)));
    }

    @Override
    public ResponseEntity<Void> markAllNotificationsAsRead() {
        notifications.markAllAsRead(currentUser.requireUserId());
        return ResponseEntity.noContent().build();
    }

    /** Réservé aux services (jeton de service) et aux administrateurs : voir SecurityConfig. */
    @Override
    public ResponseEntity<Notification> triggerNotification(SendNotificationRequest sendNotificationRequest) {
        String emittedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDto(notifications.send(sendNotificationRequest, emittedBy)));
    }

    static Notification toDto(NotificationEntity e) {
        Notification dto = new Notification();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setType(e.getType());
        dto.setChannel(e.getChannel());
        dto.setTitle(e.getTitle());
        dto.setContent(e.getContent());
        dto.setReferenceId(e.getReferenceId());
        dto.setIsRead(e.isRead());
        dto.setCreatedAt(e.getCreatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }
}
