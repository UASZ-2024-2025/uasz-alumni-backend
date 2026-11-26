package com.uasz.alumni.user.service;

import java.time.Duration;

/** Envoi des emails transactionnels (abstraction : facilite les tests et un autre fournisseur). */
public interface EmailService {

    void sendConfirmationEmail(String to, String firstName, String token);

    void sendPasswordResetEmail(String to, String firstName, String token, Duration ttl);

    /** Compte créé par un administrateur : le lien permet de définir le mot de passe et active le compte. */
    void sendAccountInvitationEmail(String to, String firstName, String token, Duration ttl);

    void sendPasswordChangedNotice(String to, String firstName);
}
