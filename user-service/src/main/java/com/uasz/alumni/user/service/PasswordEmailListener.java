package com.uasz.alumni.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie les emails liés au mot de passe après le commit, en asynchrone : une panne SMTP ne fait pas
 * échouer l'opération, et le temps de réponse de /auth/forgot-password ne révèle pas si le compte existe.
 */
@Component
public class PasswordEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordEmailListener.class);

    private final EmailService emailService;

    public PasswordEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordEmailRequested(PasswordEmailRequested event) {
        try {
            switch (event.kind()) {
                case RESET -> emailService.sendPasswordResetEmail(
                        event.email(), event.firstName(), event.token(), event.ttl());
                case INVITATION -> emailService.sendAccountInvitationEmail(
                        event.email(), event.firstName(), event.token(), event.ttl());
            }
        } catch (RuntimeException e) {
            log.error("Échec d'envoi de l'email ({}) à {} : {}", event.kind(), event.email(), e.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChanged(PasswordChangedNotice event) {
        try {
            emailService.sendPasswordChangedNotice(event.email(), event.firstName());
        } catch (RuntimeException e) {
            log.error("Échec d'envoi de l'avis de changement de mot de passe à {} : {}", event.email(), e.getMessage());
        }
    }
}
