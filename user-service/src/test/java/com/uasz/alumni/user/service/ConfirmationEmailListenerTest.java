package com.uasz.alumni.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfirmationEmailListenerTest {

    @Mock EmailService emailService;

    private final ConfirmationEmailRequested event =
            new ConfirmationEmailRequested("awa.diop@uasz.sn", "Awa", "jeton-en-clair");

    @Test
    void sendsTheConfirmationEmail() {
        new ConfirmationEmailListener(emailService).onConfirmationEmailRequested(event);

        verify(emailService).sendConfirmationEmail("awa.diop@uasz.sn", "Awa", "jeton-en-clair");
    }

    @Test
    void anSmtpFailureNeverPropagates() {
        doThrow(new IllegalStateException("SMTP down"))
                .when(emailService).sendConfirmationEmail("awa.diop@uasz.sn", "Awa", "jeton-en-clair");

        assertThatCode(() -> new ConfirmationEmailListener(emailService).onConfirmationEmailRequested(event))
                .doesNotThrowAnyException();
    }
}
