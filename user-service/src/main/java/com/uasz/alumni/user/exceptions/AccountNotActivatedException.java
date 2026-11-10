package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 403 : l'adresse email n'a pas encore été confirmée. */
public class AccountNotActivatedException extends ApiException {
    public AccountNotActivatedException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVATED", "Votre compte n'est pas encore activé. Confirmez votre adresse email.");
    }
}
