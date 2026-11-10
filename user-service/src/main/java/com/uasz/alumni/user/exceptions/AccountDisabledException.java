package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 403 : compte INACTIF ou SUSPENDU. */
public class AccountDisabledException extends ApiException {
    public AccountDisabledException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Votre compte est désactivé ou suspendu.");
    }
}
