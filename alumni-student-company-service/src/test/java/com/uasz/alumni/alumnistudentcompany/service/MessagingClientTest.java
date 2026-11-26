package com.uasz.alumni.alumnistudentcompany.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

/** Client réel (proxy {@code @Retryable} compris) contre une fausse messagerie HTTP (ALUM-246, ADR-0006). */
class MessagingClientTest {

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    /** Codes HTTP à renvoyer dans l'ordre ; vide : 200. */
    private final ConcurrentLinkedQueue<Integer> failures = new ConcurrentLinkedQueue<>();
    private volatile String conversations = "[]";
    private volatile String groups = "[]";

    private record Received(String path, String authorization) {
    }

    @Configuration
    @EnableResilientMethods
    static class ResilienceOnly {
    }

    @BeforeEach
    void startFakeMessaging() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            String path = exchange.getRequestURI().getPath();
            received.add(new Received(path, exchange.getRequestHeaders().getFirst("Authorization")));
            Integer failure = failures.poll();
            int status = failure == null ? 200 : failure;
            String body = status != 200 ? "{\"code\":\"X\",\"message\":\"x\"}"
                    : path.endsWith("/groups") ? groups : conversations;
            byte[] json = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopFakeMessaging() {
        server.stop(0);
    }

    private void withClient(Consumer<MessagingClient> test) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(ResilienceOnly.class, MessagingClient.class)
                .withPropertyValues(
                        "notification-messaging-service.base-url=http://127.0.0.1:"
                                + server.getAddress().getPort() + "/v1",
                        "notification-messaging-service.connect-timeout=1s",
                        "notification-messaging-service.read-timeout=1s",
                        "notification-messaging-service.retry.max-retries=1",
                        "notification-messaging-service.retry.delay-ms=10",
                        "notification-messaging-service.retry.multiplier=2",
                        "notification-messaging-service.retry.max-delay-ms=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(MessagingClient.class));
                });
    }

    @Test
    void additionneLesNonLusDesConversationsEtDesGroupes() {
        conversations = "[{\"unreadCount\":2},{\"unreadCount\":3},{\"unreadCount\":0}]";
        groups = "[{\"unreadCount\":4}]";

        withClient(client -> assertThat(client.unreadMessages("jeton-alumni")).isEqualTo(9));
    }

    @Test
    void propageLeJetonDeLUtilisateurSurLesDeuxAppels() {
        withClient(client -> client.unreadMessages("jeton-alumni"));

        assertThat(received).extracting(Received::path)
                .containsExactly("/v1/messages/conversations", "/v1/messages/groups");
        assertThat(received).extracting(Received::authorization).containsOnly("Bearer jeton-alumni");
    }

    @Test
    void compteurAbsentCommeZero() {
        conversations = "[{\"recipientName\":\"Awa\"}]";
        groups = "[]";

        withClient(client -> assertThat(client.unreadMessages("jeton-alumni")).isZero());
    }

    @Test
    void rejoueUneErreurTransitoirePuisReussit() {
        failures.add(503);
        conversations = "[{\"unreadCount\":1}]";

        withClient(client -> assertThat(client.unreadMessages("jeton-alumni")).isEqualTo(1));

        assertThat(received).hasSize(3); // conversations (503), puis conversations + groupes
    }

    @Test
    void abandonneApresUnSeulRetry() {
        failures.addAll(List.of(503, 503));

        withClient(client -> assertThatThrownBy(() -> client.unreadMessages("jeton-alumni"))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class));

        assertThat(received).hasSize(2);
    }

    @Test
    void neRejouePasUnJetonRefuse() {
        failures.add(401);

        withClient(client -> assertThatThrownBy(() -> client.unreadMessages("jeton-alumni"))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class));

        assertThat(received).hasSize(1);
    }
}
