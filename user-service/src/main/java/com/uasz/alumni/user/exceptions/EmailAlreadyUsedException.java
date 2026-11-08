package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 409 : l'adresse email est déjà associée à un compte. */
public class EmailAlreadyUsedException extends ApiException {

    public EmailAlreadyUsedException() {
        super(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "Cette adresse email est déjà utilisée.");
    }
}
