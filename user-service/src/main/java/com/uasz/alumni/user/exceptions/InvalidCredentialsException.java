package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 401 : email inconnu ou mot de passe incorrect (un seul message : pas d'énumération de comptes). */
public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email ou mot de passe incorrect.");
    }
}
