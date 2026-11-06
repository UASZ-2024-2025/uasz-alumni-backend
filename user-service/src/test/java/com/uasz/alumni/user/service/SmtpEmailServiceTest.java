package com.uasz.alumni.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.uasz.alumni.user.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock JavaMailSender mailSender;

    @Test
    void sendConfirmationEmail_containsFrontendLinkWithToken() {
        AppProperties properties = new AppProperties("http://localhost:4200", "no-reply@alumni-uasz.sn", Duration.ofHours(24));
        SmtpEmailService service = new SmtpEmailService(mailSender, properties);

        service.sendConfirmationEmail("awa.diop@uasz.sn", "Awa", "tok123");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getTo()).containsExactly("awa.diop@uasz.sn");
        assertThat(message.getFrom()).isEqualTo("no-reply@alumni-uasz.sn");
        assertThat(message.getText())
                .contains("Bonjour Awa")
                .contains("http://localhost:4200/confirmation-email?token=tok123")
                .contains("24 heures");
    }
}
