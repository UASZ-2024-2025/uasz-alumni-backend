package com.uasz.alumni.alumnistudentcompany.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.uasz.alumni.alumnistudentcompany.jwt.ServiceTokenProvider;
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

/** Client réel (proxy {@code @Retryable} compris) contre un faux user-service HTTP (ALUM-168, ADR-0006). */
class UserServiceClientTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final UUID USER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID COMPANY = UUID.fromString("0b8a3c1e-5d2f-4a7e-9c11-3f6d8e2a4b90");

    private HttpServer server;
    private final ConcurrentLinkedQueue<Integer> replies = new ConcurrentLinkedQueue<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile int last = 200;

    private record Received(String method, String path, String authorization, String body) {
    }

    @Configuration
    @EnableResilientMethods
    static class ResilienceOnly {
    }

    @BeforeEach
    void startFakeService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/internal/users", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), body));
            Integer next = replies.poll();
            int status = next == null ? last : next;
            last = status;
            byte[] json = "{\"code\":\"X\",\"message\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopFakeService() {
        server.stop(0);
    }

    private void withClient(Consumer<UserServiceClient> test) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(ResilienceOnly.class, UserServiceClient.class, ServiceTokenProvider.class)
                .withPropertyValues(
                        "spring.application.name=alumni-student-company-service",
                        "jwt.secret=" + SECRET,
                        "jwt.issuer=alumni-uasz-user",
                        "service-token.issuer=alumni-uasz-internal",
                        "service-token.ttl=5m",
                        "user-service.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "user-service.connect-timeout=1s",
                        "user-service.read-timeout=1s",
                        "user-service.retry.max-retries=3",
                        "user-service.retry.delay-ms=10",
                        "user-service.retry.multiplier=2",
                        "user-service.retry.max-delay-ms=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(UserServiceClient.class));
                });
    }

    @Test
    void putLeRattachementAvecUnJetonDeService() {
        withClient(client -> client.linkCompany(USER, COMPANY));

        assertThat(received).hasSize(1);
        Received call = received.getFirst();
        assertThat(call.method()).isEqualTo("PUT");
        assertThat(call.path()).isEqualTo("/v1/internal/users/" + USER + "/company");
        assertThat(call.body()).contains("\"companyId\":\"" + COMPANY + "\"");

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build()
                .parseSignedClaims(call.authorization().substring("Bearer ".length()))
                .getPayload();
        assertThat(claims.getIssuer()).isEqualTo("alumni-uasz-internal");
        assertThat(claims.getAudience()).containsExactly("user-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    void rejoueLesErreursTransitoiresPuisReussit() {
        replies.addAll(List.of(503, 500, 200));

        withClient(client -> client.linkCompany(USER, COMPANY));

        assertThat(received).hasSize(3);
    }

    @Test
    void abandonneApresTroisRetries() {
        replies.add(503);

        withClient(client -> assertThatThrownBy(() -> client.linkCompany(USER, COMPANY))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class));

        assertThat(received).hasSize(4);
    }

    @Test
    void neRejouePasUnConflit() {
        replies.add(409);

        withClient(client -> assertThatThrownBy(() -> client.linkCompany(USER, COMPANY))
                .isInstanceOf(HttpClientErrorException.Conflict.class));

        assertThat(received).hasSize(1);
    }
}
