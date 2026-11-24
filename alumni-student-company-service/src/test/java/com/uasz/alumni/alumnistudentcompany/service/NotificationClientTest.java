package com.uasz.alumni.alumnistudentcompany.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.uasz.alumni.alumnistudentcompany.jwt.ServiceTokenProvider;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/** Client réel (proxy {@code @Retryable} compris) contre un faux notification-messaging-service (ALUM-119). */
class NotificationClientTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final UUID STUDENT = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    private HttpServer server;
    private final ConcurrentLinkedQueue<Integer> replies = new ConcurrentLinkedQueue<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile int last = 201;

    private record Received(String path, String authorization, String body) {
    }

    @Configuration
    @EnableResilientMethods
    static class ResilienceOnly {
    }

    @BeforeEach
    void startFakeService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/notifications/send", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), body));
            Integer next = replies.poll();
            int status = next == null ? last : next;
            last = status;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopFakeService() {
        server.stop(0);
    }

    private static SendNotificationRequest request() {
        return new SendNotificationRequest(STUDENT, NotificationType.SYSTEM_ALERT, "Titre", "Contenu");
    }

    private void withClient(Consumer<NotificationClient> test) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(ResilienceOnly.class, NotificationClient.class, ServiceTokenProvider.class)
                .withPropertyValues(
                        "spring.application.name=alumni-student-company-service",
                        "jwt.secret=" + SECRET,
                        "jwt.issuer=alumni-uasz-user",
                        "service-token.issuer=alumni-uasz-internal",
                        "service-token.ttl=5m",
                        "notification.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "notification.connect-timeout=1s",
                        "notification.read-timeout=1s",
                        "notification.retry.max-retries=3",
                        "notification.retry.delay-ms=10",
                        "notification.retry.multiplier=2",
                        "notification.retry.max-delay-ms=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(NotificationClient.class));
                });
    }

    @Test
    void posteLaNotificationAvecUnJetonDeService() {
        withClient(client -> client.send(request()));

        assertThat(received).hasSize(1);
        Received call = received.getFirst();
        assertThat(call.path()).isEqualTo("/v1/notifications/send");
        assertThat(call.body()).contains(STUDENT.toString()).contains("SYSTEM_ALERT");

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build()
                .parseSignedClaims(call.authorization().substring("Bearer ".length()))
                .getPayload();
        assertThat(claims.getAudience()).containsExactly("notification-messaging-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    void rejoueLesErreursTransitoiresPuisReussit() {
        replies.addAll(List.of(503, 500, 201));

        withClient(client -> client.send(request()));

        assertThat(received).hasSize(3);
    }

    @Test
    void abandonneApresTroisRetries() {
        replies.add(503);

        withClient(client -> assertThatThrownBy(() -> client.send(request()))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class));

        assertThat(received).hasSize(4);
    }

    @Test
    void neRejouePasUneErreur4xx() {
        replies.add(400);

        withClient(client -> assertThatThrownBy(() -> client.send(request()))
                .isInstanceOf(HttpClientErrorException.BadRequest.class));

        assertThat(received).hasSize(1);
    }
}
