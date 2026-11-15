package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 400 : jeton de réinitialisation inconnu, expiré ou déjà utilisé. */
public class InvalidResetTokenException extends ApiException {
    public InvalidResetTokenException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_TOKEN", "Le lien de réinitialisation est invalide ou a expiré.");
    }
}
