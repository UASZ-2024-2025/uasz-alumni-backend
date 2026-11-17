package com.uasz.alumni.user.exceptions;

import org.springframework.http.HttpStatus;

/** Mot de passe non conforme : 422 pour reset/change (contrat), 400 pour l'inscription. */
public class WeakPasswordException extends ApiException {
    public WeakPasswordException(HttpStatus status) {
        super(status, "WEAK_PASSWORD",
                "Le mot de passe doit faire de 8 à 72 caractères et contenir au moins une lettre et un chiffre.");
    }
}
