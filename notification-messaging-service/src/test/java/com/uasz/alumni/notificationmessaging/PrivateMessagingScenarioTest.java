package com.uasz.alumni.notificationmessaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.uasz.alumni.notificationmessaging.service.PrivateMessagingService;
import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = {
        "SERVER_PORT=0",
        "FORWARD_HEADERS_STRATEGY=none",
        "API_PREFIX=/v1",
        "CORS_ALLOWED_ORIGINS=http://localhost:4200",
        "JWT_SECRET=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=",
        "JWT_ISSUER=alumni-uasz-user"
})
class PrivateMessagingScenarioTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

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

        assertThrows(ResponseStatusException.class, () -> service.delete(b, msg.getId()));
        assertThrows(ResponseStatusException.class, () -> service.send(a, a, "moi"));
        service.delete(a, msg.getId());
    }
}