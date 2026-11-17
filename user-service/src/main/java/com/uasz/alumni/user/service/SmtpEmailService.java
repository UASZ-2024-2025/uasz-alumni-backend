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

    /** Route frontend qui lit ?token= et appelle POST /auth/reset-password (à aligner avec votre frontend). */
    private static final String RESET_PATH = "/reinitialisation-mot-de-passe";

    @Override
    public void sendPasswordResetEmail(String to, String firstName, String token, Duration ttl) {
        send(to, "Alumni UASZ : réinitialisation de votre mot de passe", """
                Bonjour %s,

                Une réinitialisation du mot de passe de votre compte Alumni UASZ a été demandée.

                Pour choisir un nouveau mot de passe, ouvrez ce lien :
                %s

                Ce lien est valable %s. Si vous n'êtes pas à l'origine de cette demande, ignorez ce message :
                votre mot de passe reste inchangé.
                """.formatted(firstName, link(RESET_PATH, token), humanize(ttl)));
    }

    @Override
    public void sendAccountInvitationEmail(String to, String firstName, String token, Duration ttl) {
        send(to, "Alumni UASZ : activez votre compte", """
                Bonjour %s,

                Un compte vient d'être créé pour vous sur la plateforme Alumni de l'Université Assane Seck de Ziguinchor.

                Pour activer votre compte et choisir votre mot de passe, ouvrez ce lien :
                %s

                Ce lien est valable %s. Si vous n'attendiez pas cet email, ignorez-le : aucun accès ne sera ouvert.
                """.formatted(firstName, link(RESET_PATH, token), humanize(ttl)));
    }

    @Override
    public void sendPasswordChangedNotice(String to, String firstName) {
        send(to, "Alumni UASZ : votre mot de passe a été modifié", """
                Bonjour %s,

                Le mot de passe de votre compte Alumni UASZ vient d'être modifié.

                Ce n'était pas vous ? Réinitialisez immédiatement votre mot de passe via « Mot de passe oublié »
                et contactez l'administration.
                """.formatted(firstName));
    }

    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path(path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
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
