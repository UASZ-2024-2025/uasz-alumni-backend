package com.uasz.alumni.user.service;

/** Envoi des emails transactionnels (abstraction : facilite les tests et un autre fournisseur). */
public interface EmailService {

    void sendConfirmationEmail(String to, String firstName, String token);
}
