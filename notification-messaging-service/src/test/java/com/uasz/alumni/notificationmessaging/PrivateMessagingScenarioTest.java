package com.uasz.alumni.notificationmessaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
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
