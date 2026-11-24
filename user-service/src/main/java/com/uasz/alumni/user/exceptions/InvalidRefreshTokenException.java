package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 401 : refresh token inconnu, expiré, révoqué ou compte non actif. */
public class InvalidRefreshTokenException extends ApiException {
    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token invalide ou expiré.");
    }
}
