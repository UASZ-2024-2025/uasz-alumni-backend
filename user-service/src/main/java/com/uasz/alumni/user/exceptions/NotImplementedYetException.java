package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** 501 : endpoint du contrat dont le ticket Jira n'est pas encore implémenté. */
public class NotImplementedYetException extends ApiException {

    public NotImplementedYetException(String ticket) {
        super(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "Fonctionnalité pas encore implémentée (" + ticket + ").");
    }
}
