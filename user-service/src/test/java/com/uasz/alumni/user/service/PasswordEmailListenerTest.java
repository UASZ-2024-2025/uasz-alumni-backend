
package com.uasz.alumni.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class PasswordEmailListenerTest {

    private static final Duration TTL = Duration.ofHours(1);

    @Mock EmailService emailService;

    @Test
    void resetEvent_sendsResetEmail() {
        new PasswordEmailListener(emailService).onPasswordEmailRequested(
                new PasswordEmailRequested("awa@uasz.sn", "Awa", "tok", PasswordEmailRequested.Kind.RESET, TTL));

        verify(emailService).sendPasswordResetEmail("awa@uasz.sn", "Awa", "tok", TTL);
    }

    @Test
    void invitationEvent_sendsInvitationEmail() {
        new PasswordEmailListener(emailService).onPasswordEmailRequested(
                new PasswordEmailRequested("rh@gainde.sn", "Awa", "tok", PasswordEmailRequested.Kind.INVITATION, TTL));

        verify(emailService).sendAccountInvitationEmail("rh@gainde.sn", "Awa", "tok", TTL);
    }

    @Test
    void changedEvent_sendsNotice() {
        new PasswordEmailListener(emailService).onPasswordChanged(new PasswordChangedNotice("awa@uasz.sn", "Awa"));

        verify(emailService).sendPasswordChangedNotice("awa@uasz.sn", "Awa");
    }

    @Test
    void smtpFailure_isSwallowed_soTheBusinessOperationIsNotAffected() {
        doThrow(new RuntimeException("smtp down")).when(emailService)
                .sendPasswordResetEmail(anyString(), anyString(), anyString(), any(Duration.class));
        doThrow(new RuntimeException("smtp down")).when(emailService)
                .sendPasswordChangedNotice(anyString(), anyString());
        PasswordEmailListener listener = new PasswordEmailListener(emailService);

        assertThatCode(() -> listener.onPasswordEmailRequested(
                new PasswordEmailRequested("a@uasz.sn", "A", "tok", PasswordEmailRequested.Kind.RESET, TTL)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.onPasswordChanged(new PasswordChangedNotice("a@uasz.sn", "A")))
                .doesNotThrowAnyException();
    }

    @Test
    void listeners_runAfterCommit_andAsynchronously() throws Exception {
        for (Method m : new Method[] {
                PasswordEmailListener.class.getDeclaredMethod("onPasswordEmailRequested", PasswordEmailRequested.class),
                PasswordEmailListener.class.getDeclaredMethod("onPasswordChanged", PasswordChangedNotice.class)}) {
            assertThat(m.isAnnotationPresent(Async.class)).as(m.getName() + " @Async").isTrue();
            assertThat(m.getAnnotation(TransactionalEventListener.class).phase())
                    .isEqualTo(TransactionPhase.AFTER_COMMIT);
        }
    }

    @Test
    void eventToString_neverLeaksTheToken() {
        String text = new PasswordEmailRequested("a@uasz.sn", "A", "SECRET-TOKEN",
                PasswordEmailRequested.Kind.RESET, TTL).toString();

        assertThat(text).doesNotContain("SECRET-TOKEN").contains("a@uasz.sn");
    }
}
