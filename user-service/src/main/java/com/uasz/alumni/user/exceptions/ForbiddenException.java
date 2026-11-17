package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 403 : permissions insuffisantes. */
public class ForbiddenException extends ApiException {
    public ForbiddenException() {
        this("Accès refusé : permissions insuffisantes.");
    }

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}
