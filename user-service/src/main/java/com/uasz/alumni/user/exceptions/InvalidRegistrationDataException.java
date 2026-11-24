package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 400 : données d'inscription refusées avant d'atteindre la base (nom vide ou trop long, mot de passe trop long...). */
public class InvalidRegistrationDataException extends ApiException {

    public InvalidRegistrationDataException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REGISTRATION_DATA", message);
    }
}
