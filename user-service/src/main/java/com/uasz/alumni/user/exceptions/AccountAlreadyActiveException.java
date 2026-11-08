package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 409 : le compte est déjà activé, il n'y a rien à confirmer. */
public class AccountAlreadyActiveException extends ApiException {

    public AccountAlreadyActiveException() {
        super(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_ACTIVE", "Ce compte est déjà activé.");
    }
}
