package com.uasz.alumni.notificationmessaging.service;

import java.util.UUID;

/** Notification du canal EMAIL enregistrée : l'e-mail part après le commit. */
public record EmailNotificationRequested(UUID userId, String title, String content) {
}
