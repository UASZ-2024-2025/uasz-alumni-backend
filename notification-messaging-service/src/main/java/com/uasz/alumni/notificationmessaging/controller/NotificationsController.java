package com.uasz.alumni.notificationmessaging.controller;

import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import com.uasz.alumni.notificationmessaging.service.ChatUserDirectory;
import com.uasz.alumni.notificationmessaging.service.NotificationCenterService;
import com.uasz.alumni.notificationmessaging.service.NotificationService;
import com.uasz.alumni.notificationmessaging.spi.api.NotificationsApi;
import com.uasz.alumni.notificationmessaging.spi.model.Notification;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationPagedResponse;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import com.uasz.alumni.notificationmessaging.spi.model.UnreadCountResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationsController implements NotificationsApi {

    private final NotificationService notifications;
    private final NotificationCenterService center;
    private final ChatUserDirectory directory;

    public NotificationsController(NotificationService notifications, NotificationCenterService center,
                                   ChatUserDirectory directory) {
        this.notifications = notifications;
        this.center = center;
        this.directory = directory;
    }

    @Override
    public ResponseEntity<NotificationPagedResponse> getUserNotifications(Boolean unreadOnly, Integer page, Integer size) {
        Page<NotificationEntity> result = center.list(caller(), unreadOnly, page, size);
        return ResponseEntity.ok(new NotificationPagedResponse()
                .content(result.getContent().stream().map(NotificationService::toDto).toList())
                .totalElements((int) Math.min(result.getTotalElements(), Integer.MAX_VALUE))
                .page(result.getNumber())
                .size(result.getSize()));
    }

    @Override
    public ResponseEntity<UnreadCountResponse> getUnreadNotificationCount() {
        long count = center.unreadCount(caller());
        return ResponseEntity.ok(new UnreadCountResponse().unreadCount((int) Math.min(count, Integer.MAX_VALUE)));
    }

    @Override
    public ResponseEntity<Notification> markNotificationAsRead(UUID id) {
        return ResponseEntity.ok(NotificationService.toDto(center.markRead(id, caller())));
    }

    @Override
    public ResponseEntity<Void> markAllNotificationsAsRead() {
        center.markAllRead(caller());
        return ResponseEntity.noContent().build();
    }

    /** Services internes (jeton de service) et administrateur : la règle est dans SecurityConfig (ALUM-83). */
    @Override
    public ResponseEntity<Notification> triggerNotification(SendNotificationRequest sendNotificationRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(NotificationService.toDto(notifications.send(sendNotificationRequest)));
    }

    /**
     * Id de l'utilisateur connecté. Son nom et son e-mail sont enregistrés dans l'annuaire local : la cloche
     * interroge le compteur sur chaque page, ce qui fait connaître l'adresse du canal EMAIL (ALUM-135).
     */
    private UUID caller() {
        AuthenticatedUser user = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        directory.remember(user);
        return user.id();
    }
}
