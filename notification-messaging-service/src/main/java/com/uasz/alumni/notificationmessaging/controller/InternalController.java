package com.uasz.alumni.notificationmessaging.controller;


import com.uasz.alumni.notificationmessaging.service.GroupService;
import com.uasz.alumni.notificationmessaging.service.NotificationCenterService;
import com.uasz.alumni.notificationmessaging.service.PrivateMessagingService;
import com.uasz.alumni.notificationmessaging.spi.api.InternalApi;
import com.uasz.alumni.notificationmessaging.spi.model.UserUnreadCounts;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints internes (jeton de service, ADR-0010) : la règle d'accès est dans SecurityConfig. */
@RestController
public class InternalController implements InternalApi {

    private final NotificationCenterService notifications;
    private final PrivateMessagingService privateMessages;
    private final GroupService groups;

    public InternalController(NotificationCenterService notifications, PrivateMessagingService privateMessages,
                              GroupService groups) {
        this.notifications = notifications;
        this.privateMessages = privateMessages;
        this.groups = groups;
    }

    @Override
    public ResponseEntity<UserUnreadCounts> getUnreadCountsForUser(UUID userId) {
        long messages = privateMessages.unreadTotal(userId) + groups.unreadTotal(userId);
        return ResponseEntity.ok(new UserUnreadCounts()
            .notifications(toInt(notifications.unreadCount(userId)))
            .messages(toInt(messages)));
    }

    private static int toInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
