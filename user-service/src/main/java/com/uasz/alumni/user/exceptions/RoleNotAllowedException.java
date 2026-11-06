package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 400 : ce rôle ne peut pas être choisi à l'inscription (ex. ADMINISTRATEUR). */
public class RoleNotAllowedException extends ApiException {

    public RoleNotAllowedException() {
        super(HttpStatus.BAD_REQUEST, "ROLE_NOT_ALLOWED",
                "Ce rôle ne peut pas être choisi lors de l'inscription.");
    }
}
