
package com.uasz.alumni.user.service;

import static com.uasz.alumni.user.UserTestData.appProps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Emails ajoutés par AL-20 (le test de l'email de confirmation reste dans SmtpEmailServiceTest). */
@ExtendWith(MockitoExtension.class)
class SmtpEmailServicePasswordTest {

    @Mock JavaMailSender mailSender;

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void passwordResetEmail_containsFrontendLinkWithToken_andValidity() {
        new SmtpEmailService(mailSender, appProps())
                .sendPasswordResetEmail("awa.diop@uasz.sn", "Awa", "tok123", Duration.ofHours(1));

        SimpleMailMessage message = sent();
        assertThat(message.getTo()).containsExactly("awa.diop@uasz.sn");
        assertThat(message.getFrom()).isEqualTo("no-reply@alumni-uasz.sn");
        assertThat(message.getSubject()).isEqualTo("Alumni UASZ : réinitialisation de votre mot de passe");
        assertThat(message.getText())
                .contains("Bonjour Awa")
                .contains("http://localhost:4200/reinitialisation-mot-de-passe?token=tok123")
                .contains("1 heure");
    }

    @Test
    void invitationEmail_containsFrontendLinkWithToken_andValidity() {
        new SmtpEmailService(mailSender, appProps())
                .sendAccountInvitationEmail("rh@gainde.sn", "Awa", "tok456", Duration.ofHours(24));

        SimpleMailMessage message = sent();
        assertThat(message.getTo()).containsExactly("rh@gainde.sn");
        assertThat(message.getSubject()).isEqualTo("Alumni UASZ : activez votre compte");
        assertThat(message.getText())
                .contains("Bonjour Awa")
                .contains("http://localhost:4200/reinitialisation-mot-de-passe?token=tok456")
                .contains("24 heures");
    }

    @Test
    void passwordChangedNotice_warnsAndNeverContainsALink() {
        new SmtpEmailService(mailSender, appProps()).sendPasswordChangedNotice("awa.diop@uasz.sn", "Awa");

        SimpleMailMessage message = sent();
        assertThat(message.getSubject()).isEqualTo("Alumni UASZ : votre mot de passe a été modifié");
        assertThat(message.getText()).contains("Bonjour Awa").contains("modifié").doesNotContain("http");
    }
}
