package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 400 : jeton de confirmation inconnu, expiré ou déjà utilisé (un seul message : pas d'indice pour un attaquant). */
public class InvalidOrExpiredTokenException extends ApiException {

    public InvalidOrExpiredTokenException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_TOKEN", "Le lien de confirmation est invalide ou a expiré.");
    }
}
