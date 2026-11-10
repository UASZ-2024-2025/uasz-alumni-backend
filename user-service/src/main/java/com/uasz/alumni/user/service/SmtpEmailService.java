package com.uasz.alumni.user.service;

import com.uasz.alumni.user.config.AppProperties;
import java.time.Duration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/** Envoi par SMTP (Mailpit en développement, serveur réel en production). */
@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public SmtpEmailService(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendConfirmationEmail(String to, String firstName, String token) {
        String link = UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path("/confirmation-email")
                .queryParam("token", token)
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to);
        message.setSubject("Alumni UASZ : confirmez votre adresse email");
        message.setText("""
                Bonjour %s,

                Merci de vous être inscrit(e) sur la plateforme Alumni de l'Université Assane Seck de Ziguinchor.

                Pour activer votre compte, confirmez votre adresse email en ouvrant ce lien :
                %s

                Ce lien est valable %s. Si vous n'êtes pas à l'origine de cette inscription, ignorez ce message.
                """.formatted(firstName, link, humanize(properties.confirmationTtl())));
        mailSender.send(message);
    }

    private static String humanize(Duration ttl) {
        long hours = ttl.toHours();
        if (hours >= 1) {
            return hours + (hours == 1 ? " heure" : " heures");
        }
        long minutes = Math.max(1, ttl.toMinutes());
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
