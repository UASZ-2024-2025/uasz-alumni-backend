package com.uasz.alumni.notificationmessaging.controller;

import com.uasz.alumni.notificationmessaging.exceptions.NotImplementedYetException;
import com.uasz.alumni.notificationmessaging.spi.api.NotificationsApi;
import com.uasz.alumni.notificationmessaging.spi.model.Notification;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationPagedResponse;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import com.uasz.alumni.notificationmessaging.spi.model.UnreadCountResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationsController implements NotificationsApi {

    @Override
    public ResponseEntity<NotificationPagedResponse> getUserNotifications(Boolean unreadOnly, Integer page, Integer size) {
        // TODO : notifications de l'utilisateur, paginées
        throw new NotImplementedYetException("getUserNotifications");
    }

    @Override
    public ResponseEntity<UnreadCountResponse> getUnreadNotificationCount() {
        // TODO : nombre de non-lues
        throw new NotImplementedYetException("getUnreadNotificationCount");
    }

    @Override
    public ResponseEntity<Notification> markNotificationAsRead(UUID id) {
        // TODO : marquer comme lue -> 200 / 404
        throw new NotImplementedYetException("markNotificationAsRead");
    }

    @Override
    public ResponseEntity<Void> markAllNotificationsAsRead() {
        // TODO : tout marquer comme lu -> 204
        throw new NotImplementedYetException("markAllNotificationsAsRead");
    }

    @Override
    public ResponseEntity<Notification> triggerNotification(SendNotificationRequest sendNotificationRequest) {
        // TODO : émission inter-services/admin (canal IN_APP/EMAIL/PUSH) -> 201 / 400
        throw new NotImplementedYetException("triggerNotification");
    }
}
