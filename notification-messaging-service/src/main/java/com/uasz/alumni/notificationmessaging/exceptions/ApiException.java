package com.uasz.alumni.notificationmessaging.exceptions;

import org.springframework.http.HttpStatus;

/** Erreur métier de base : porte le statut HTTP et un code applicatif (champ "code" de ErrorResponse). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
