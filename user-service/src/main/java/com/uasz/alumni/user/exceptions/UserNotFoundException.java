package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 404 : utilisateur introuvable. */
public class UserNotFoundException extends ApiException {
    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Utilisateur introuvable.");
    }
}
