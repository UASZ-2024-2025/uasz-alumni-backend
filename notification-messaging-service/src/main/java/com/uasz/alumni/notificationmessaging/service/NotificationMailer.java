package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.model.ChatUser;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Canal EMAIL (ALUM-135) : envoie la notification à l'adresse connue du destinataire.
 *
 * <ul>
 *   <li>Après le commit : un e-mail ne part jamais pour une notification annulée.</li>
 *   <li>L'adresse vient de {@code chat_users}, recopiée du jeton à chaque appel de l'utilisateur : aucun appel à
 *       user-service. Un compte qui ne s'est encore jamais connecté depuis n'a pas d'adresse : l'e-mail est sauté.</li>
 *   <li>Sans serveur SMTP configuré ({@code MAIL_HOST} vide), l'e-mail est sauté et journalisé.</li>
 *   <li>Un échec SMTP est journalisé : la notification reste dans le centre de notifications.</li>
 * </ul>
 */
@Component
public class NotificationMailer {

    private static final Logger log = LoggerFactory.getLogger(NotificationMailer.class);

    private final ChatUserDirectory directory;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;
    private final String frontendUrl;

    public NotificationMailer(ChatUserDirectory directory, ObjectProvider<JavaMailSender> mailSender,
                              @Value("${spring.mail.host:}") String host,
                              @Value("${notification.mail.from:no-reply@alumni-uasz.sn}") String from,
                              @Value("${notification.frontend-url:}") String frontendUrl) {
        this.directory = directory;
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailRequested(EmailNotificationRequested event) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (host == null || host.isBlank() || sender == null) {
            log.info("E-mail de notification non envoyé (aucun serveur SMTP configuré) pour {}", event.userId());
            return;
        }
        Optional<String> address = Optional.ofNullable(directory.find(List.of(event.userId())).get(event.userId()))
                .map(ChatUser::getEmail).filter(e -> !e.isBlank());
        if (address.isEmpty()) {
            log.info("E-mail de notification non envoyé : adresse inconnue pour {}", event.userId());
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(address.get());
        message.setSubject("Alumni UASZ : " + event.title());
        message.setText(event.content() + (frontendUrl == null || frontendUrl.isBlank() ? ""
                : "\n\nRetrouvez vos notifications sur " + frontendUrl + "/notifications"));
        try {
            sender.send(message);
            log.info("E-mail de notification envoyé à l'utilisateur {}", event.userId());
        } catch (RuntimeException e) {
            log.error("E-mail de notification non délivré à l'utilisateur {} : {}", event.userId(), e.toString());
        }
    }
}
