package com.uasz.alumni.user.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import com.uasz.alumni.user.config.JwtProperties;
import com.uasz.alumni.user.jwt.ServiceTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.web.client.ResourceAccessException;

/**
 * Client réel (proxy {@code @Retryable} compris) contre un faux alumni-student-company-service HTTP :
 * jeton de service, timeouts, retries sur les erreurs transitoires seulement (ALUM-83, ADR-0006).
 */
class StudentCompanyClientTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final ProfileProvisioningRequest REQUEST = new ProfileProvisioningRequest(
            UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"), ProvisionedProfileType.ETUDIANT,
            "Awa", "Diop", "awa.diop@uasz.sn");

    private HttpServer server;
    /** Réponses à renvoyer dans l'ordre (la dernière est répétée) : code HTTP, délai avant réponse. */
    private final ConcurrentLinkedQueue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile Reply last = new Reply(201, 0);

    private record Reply(int status, long delayMs) {
    }

    private record Received(String method, String path, String authorization, String body) {
    }

    @Configuration
    @EnableResilientMethods
    static class ResilienceOnly {
    }

    @BeforeEach
    void startFakeService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/internal/profiles", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), body));
            Reply reply = replies.poll();
            if (reply == null) {
                reply = last;
            } else {
                last = reply;
            }
            try {
                Thread.sleep(reply.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] json = "{\"code\":\"X\",\"message\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        // plusieurs requêtes à la fois : une réponse lente ne doit pas bloquer la suivante
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopFakeService() {
        server.stop(0);
    }

    private void respond(Reply... sequence) {
        replies.addAll(List.of(sequence));
    }

    private void withClient(Consumer<StudentCompanyClient> test) {
        new ApplicationContextRunner()
                // conversion "300ms" -> Duration, fournie automatiquement par une vraie application Spring Boot
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(ResilienceOnly.class, StudentCompanyClient.class, ServiceTokenProvider.class)
                .withBean(JwtProperties.class, () -> new JwtProperties(SECRET, "alumni-uasz-user", Duration.ofMinutes(15)))
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues(
                        "spring.application.name=user-service",
                        "service-token.issuer=alumni-uasz-internal",
                        "service-token.ttl=5m",
                        "student-company.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "student-company.connect-timeout=1s",
                        "student-company.read-timeout=1s",
                        "student-company.retry.max-retries=3",
                        "student-company.retry.delay-ms=10",
                        "student-company.retry.multiplier=2",
                        "student-company.retry.max-delay-ms=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(StudentCompanyClient.class));
                });
    }

    @Test
    void postsTheRequestWithAServiceToken() {
        respond(new Reply(201, 0));

        withClient(client -> client.provisionProfile(REQUEST));

        assertThat(received).hasSize(1);
        Received call = received.getFirst();
        assertThat(call.method()).isEqualTo("POST");
        assertThat(call.path()).isEqualTo("/v1/internal/profiles");
        assertThat(call.body()).contains("\"profileType\":\"ETUDIANT\"", "\"userId\":\"7c9e6679-7425-40de-944b-e07fc1f90ae7\"");

        // jamais le JWT de l'utilisateur : un jeton de service destiné à alumni-student-company-service
        assertThat(call.authorization()).startsWith("Bearer ");
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build()
                .parseSignedClaims(call.authorization().substring("Bearer ".length()))
                .getPayload();
        assertThat(claims.getIssuer()).isEqualTo("alumni-uasz-internal");
        assertThat(claims.getAudience()).containsExactly("alumni-student-company-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    void anExistingProfileIsASuccess() {
        respond(new Reply(200, 0)); // idempotence : profil déjà créé

        withClient(client -> client.provisionProfile(REQUEST));

        assertThat(received).hasSize(1);
    }

    @Test
    void retriesTransientServerErrorsThenSucceeds() {
        respond(new Reply(503, 0), new Reply(500, 0), new Reply(201, 0));

        withClient(client -> client.provisionProfile(REQUEST));

        assertThat(received).hasSize(3);
    }

    @Test
    void retriesAReadTimeout() {
        respond(new Reply(201, 2_000), new Reply(201, 0)); // 2 s > read-timeout (1 s)

        withClient(client -> client.provisionProfile(REQUEST));

        assertThat(received).hasSize(2);
    }

    @Test
    void givesUpAfterThreeRetries() {
        respond(new Reply(503, 0)); // service toujours indisponible

        withClient(client -> assertThatThrownBy(() -> client.provisionProfile(REQUEST))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class));

        assertThat(received).hasSize(4); // 1 essai + 3 retries
    }

    @Test
    void doesNotRetryAClientError() {
        respond(new Reply(403, 0)); // jeton refusé : rejouer ne changerait rien

        withClient(client -> assertThatThrownBy(() -> client.provisionProfile(REQUEST))
                .isInstanceOf(HttpClientErrorException.Forbidden.class));

        assertThat(received).hasSize(1);
    }

    @Test
    void doesNotRetryNotImplemented() {
        respond(new Reply(501, 0)); // opération pas encore écrite côté alumni-student-company-service

        withClient(client -> assertThatThrownBy(() -> client.provisionProfile(REQUEST))
                .isInstanceOf(HttpServerErrorException.NotImplemented.class));

        assertThat(received).hasSize(1);
    }

    @Test
    void anUnreachableServiceIsATransientError() {
        int port = server.getAddress().getPort();
        server.stop(0); // plus personne n'écoute sur ce port

        withClient(client -> assertThatThrownBy(() -> client.provisionProfile(REQUEST))
                .isInstanceOf(ResourceAccessException.class));

        assertThat(port).isPositive();
    }
}
