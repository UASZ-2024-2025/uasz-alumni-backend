package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;

/** Règle commune aux messages privés et de groupe : comme sur WhatsApp, un message se modifie peu après l'envoi. */
final class MessageEdits {

    /** Délai pendant lequel l'auteur peut modifier son message. */
    static final Duration WINDOW = Duration.ofMinutes(15);

    private MessageEdits() {
    }

    static void requireEditable(boolean deleted, OffsetDateTime sentAt, OffsetDateTime now) {
        if (deleted) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE_DELETED", "Un message supprimé ne peut pas être modifié.");
        }
        if (sentAt.plus(WINDOW).isBefore(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EDIT_WINDOW_EXPIRED",
                    "Un message ne peut être modifié que dans les " + WINDOW.toMinutes() + " minutes qui suivent son envoi.");
        }
    }
}
