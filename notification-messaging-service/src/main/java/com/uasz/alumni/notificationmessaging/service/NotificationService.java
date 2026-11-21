package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import com.uasz.alumni.notificationmessaging.repository.NotificationRepository;
import com.uasz.alumni.notificationmessaging.spi.model.Notification;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ALUM-135 : émission d'une notification ({@code POST /notifications/send}, services internes et administrateur).
 *
 * <ul>
 *   <li>Toute notification est enregistrée : elle apparaît dans le centre de notifications du destinataire.</li>
 *   <li>Canal EMAIL : elle est en plus envoyée par e-mail, après le commit (voir {@link NotificationMailer}).</li>
 *   <li>Canal PUSH : hors périmètre, la notification reste visible dans l'application.</li>
 * </ul>
 */
@Service
public class NotificationService {

    static final int MAX_TITLE = 255;
    static final int MAX_CONTENT = 4000;
    static final int MAX_REFERENCE = 255;

    private final NotificationRepository notifications;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, ApplicationEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public NotificationEntity send(SendNotificationRequest request) {
        if (request.getUserId() == null || request.getType() == null) {
            throw badRequest("userId et type sont obligatoires");
        }
        String title = required("title", request.getTitle(), MAX_TITLE);
        String content = required("content", request.getContent(), MAX_CONTENT);
        String reference = request.getReferenceId() == null || request.getReferenceId().isBlank()
                ? null : request.getReferenceId().trim();
        if (reference != null && reference.length() > MAX_REFERENCE) {
            throw badRequest("referenceId : " + MAX_REFERENCE + " caractères maximum");
        }
        NotificationChannel channel = request.getChannel() == null ? NotificationChannel.IN_APP : request.getChannel();

        NotificationEntity saved = notifications.save(new NotificationEntity(request.getUserId(), request.getType(),
                channel, title, content, reference, OffsetDateTime.now(clock)));
        if (channel == NotificationChannel.EMAIL) {
            events.publishEvent(new EmailNotificationRequested(saved.getUserId(), saved.getTitle(), saved.getContent()));
        }
        return saved;
    }

    public static Notification toDto(NotificationEntity n) {
        return new Notification()
                .id(n.getId())
                .userId(n.getUserId())
                .type(n.getType())
                .channel(n.getChannel())
                .title(n.getTitle())
                .content(n.getContent())
                .referenceId(n.getReferenceId())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt());
    }

    private static String required(String field, String value, int max) {
        if (value == null || value.isBlank()) {
            throw badRequest(field + " est obligatoire");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw badRequest(field + " : " + max + " caractères maximum");
        }
        return trimmed;
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
