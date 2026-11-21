package com.uasz.alumni.notificationmessaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import com.uasz.alumni.notificationmessaging.service.ChatUserDirectory;
import com.uasz.alumni.notificationmessaging.service.PrivateMessagingService;
import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "SERVER_PORT=0",
        "FORWARD_HEADERS_STRATEGY=none",
        "API_PREFIX=/v1",
        "CORS_ALLOWED_ORIGINS=http://localhost:4200",
        "JWT_SECRET=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=",
        "JWT_ISSUER=alumni-uasz-user"
})
class PrivateMessagingScenarioTest {

    private static final EmbeddedPostgres POSTGRES = demarrer();

    private static EmbeddedPostgres demarrer() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("PostgreSQL embarqué indisponible", e);
        }
    }

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void arreter() throws IOException {
        POSTGRES.close();
    }

    @Autowired
    PrivateMessagingService service;

    @Autowired
    ChatUserDirectory directory;

    @Test
    void conversationsShowTheNamesOfKnownUsers() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        directory.remember(new AuthenticatedUser(a, "awa@uasz.sn", "ETUDIANT", "Awa Diop"));

        var sent = service.send(a, b, "Bonjour");
        assertThat(sent.getSenderName()).isEqualTo("Awa Diop");
        assertThat(sent.getRecipientName()).isNull(); // b ne s'est jamais connecté au service

        List<ConversationSummary> conversations = service.listConversations(b);
        assertThat(conversations.get(0).getRecipientName()).isEqualTo("Awa Diop");
        assertThat(conversations.get(0).getRecipientRole()).isEqualTo("ETUDIANT");
        assertThat(service.getConversation(b, a, 0, 10).getContent().get(0).getSenderName()).isEqualTo("Awa Diop");
    }

    @Test
    void unreadCounterDropsToZeroAfterMarkAsRead() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        service.send(a, b, "Message 1");
        service.send(a, b, "Message 2");
        service.send(a, b, "Message 3");

        List<ConversationSummary> before = service.listConversations(b);
        assertThat(before).hasSize(1);
        assertThat(before.get(0).getRecipientId()).isEqualTo(a);
        assertThat(before.get(0).getUnreadCount()).isEqualTo(3);

        service.markAsRead(b, a);

        List<ConversationSummary> after = service.listConversations(b);
        assertThat(after.get(0).getUnreadCount()).isEqualTo(0);
    }

    @Test
    void onlySenderCanDeleteAndCannotWriteToSelf() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        var msg = service.send(a, b, "Bonjour");

        assertThrows(ApiException.class, () -> service.delete(b, msg.getId()));
        assertThrows(ApiException.class, () -> service.send(a, a, "moi"));
        service.delete(a, msg.getId());
    }
}
