package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 400 : le mot de passe actuel fourni est incorrect. */
public class InvalidCurrentPasswordException extends ApiException {
    public InvalidCurrentPasswordException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Le mot de passe actuel est incorrect.");
    }
}
