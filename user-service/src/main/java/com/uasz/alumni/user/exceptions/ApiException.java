package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** Erreur métier convertie en {@code ErrorResponse} (voir le contrat) par {@link ApiExceptionHandler}. */
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
