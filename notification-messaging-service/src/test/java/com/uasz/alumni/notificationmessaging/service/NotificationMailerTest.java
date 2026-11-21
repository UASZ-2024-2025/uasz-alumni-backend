package com.uasz.alumni.notificationmessaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.notificationmessaging.model.ChatUser;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class NotificationMailerTest {

    private final ChatUserDirectory directory = mock(ChatUserDirectory.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    private final UUID awa = UUID.randomUUID();
    private final EmailNotificationRequested event =
            new EmailNotificationRequested(awa, "Nouvelle offre", "Une offre correspond à votre profil.");

    @BeforeEach
    void setUp() {
        when(provider.getIfAvailable()).thenReturn(sender);
    }

    private NotificationMailer mailer(String host) {
        return new NotificationMailer(directory, provider, host, "no-reply@alumni-uasz.sn", "https://alumni.test");
    }

    private void adresse(String email) {
        ChatUser user = mock(ChatUser.class);
        when(user.getEmail()).thenReturn(email);
        when(directory.find(any())).thenReturn(Map.of(awa, user));
    }

    @Test
    void envoieLEmailALAdresseConnue() {
        adresse("awa@zig.univ.sn");

        mailer("smtp-relay.test").onEmailRequested(event);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("awa@zig.univ.sn");
        assertThat(sent.getValue().getSubject()).isEqualTo("Alumni UASZ : Nouvelle offre");
        assertThat(sent.getValue().getText()).contains("Une offre correspond", "https://alumni.test/notifications");
    }

    @Test
    void sansSmtpOuSansAdresse_rienNePart() {
        adresse("awa@zig.univ.sn");
        mailer("").onEmailRequested(event);

        when(directory.find(any())).thenReturn(Map.of());
        mailer("smtp-relay.test").onEmailRequested(event);

        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void unEchecSmtpNEstPasPropage() {
        adresse("awa@zig.univ.sn");
        doThrow(new MailSendException("refusé")).when(sender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailer("smtp-relay.test").onEmailRequested(event)).doesNotThrowAnyException();
    }
}
