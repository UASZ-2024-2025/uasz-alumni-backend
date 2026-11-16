package com.uasz.alumni.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie l'email de confirmation une fois la transaction validée. Une panne SMTP ne doit pas faire
 * échouer l'inscription : le compte existe, et l'utilisateur peut demander un nouvel email
 * via {@code POST /auth/resend-confirmation}.
 */
@Component
public class ConfirmationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationEmailListener.class);

    private final EmailService emailService;

    public ConfirmationEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    /** Exécuté sur le pool « mailExecutor » : la requête HTTP n'attend pas le serveur SMTP. */
    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmationEmailRequested(ConfirmationEmailRequested event) {
        try {
            emailService.sendConfirmationEmail(event.email(), event.firstName(), event.token());
        } catch (RuntimeException e) {
            log.error("Échec d'envoi de l'email de confirmation ({})", e.getClass().getSimpleName());
        }
    }
}
