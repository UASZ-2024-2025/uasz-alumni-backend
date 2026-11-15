package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 423 : trop d'échecs de connexion. */
public class AccountLockedException extends ApiException {
    public AccountLockedException() {
        super(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Compte temporairement verrouillé après trop d'échecs de connexion.");
    }
}
