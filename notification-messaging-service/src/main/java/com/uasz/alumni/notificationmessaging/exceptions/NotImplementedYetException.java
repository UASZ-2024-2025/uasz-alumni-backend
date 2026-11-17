package com.uasz.alumni.notificationmessaging.exceptions;

import org.springframework.http.HttpStatus;

/** Levée par les squelettes de controllers tant que la logique métier n'est pas écrite. */
public class NotImplementedYetException extends ApiException {

    public NotImplementedYetException(String operation) {
        super(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "Opération non implémentée : " + operation);
    }
}
