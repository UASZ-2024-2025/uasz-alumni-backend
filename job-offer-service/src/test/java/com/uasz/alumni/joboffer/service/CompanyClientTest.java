package com.uasz.alumni.joboffer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.joboffer.jwt.ServiceTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
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
import org.springframework.web.client.HttpServerErrorException;

/** Client réel (proxy {@code @Retryable} compris) contre un faux alumni-student-company-service (ALUM-148). */
class CompanyClientTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final UUID COMPANY = UUID.fromString("0b8a3c1e-5d2f-4a7e-9c11-3f6d8e2a4b90");
    private static final UUID OWNER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    private HttpServer server;
    private final ConcurrentLinkedQueue<Integer> replies = new ConcurrentLinkedQueue<>();
    private final List<String[]> received = new CopyOnWriteArrayList<>(); // {chemin, Authorization}
    private volatile int last = 200;

    @Configuration
    @EnableResilientMethods
    static class ResilienceOnly {
    }

    @BeforeEach
    void startFakeService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/internal/companies", exchange -> {
            received.add(new String[] {exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization")});
            Integer next = replies.poll();
            int status = next == null ? last : next;
            last = status;
            String json = status == 200
                    ? "{\"id\":\"" + COMPANY + "\",\"ownerId\":\"" + OWNER + "\",\"name\":\"Gainde 2000\",\"isPartner\":true}"
                    : "{\"code\":\"X\",\"message\":\"x\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopFakeService() {
        server.stop(0);
    }

    private void withClient(Consumer<CompanyClient> test) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(ResilienceOnly.class, CompanyClient.class, ServiceTokenProvider.class)
                .withPropertyValues(
                        "spring.application.name=job-offer-service",
                        "jwt.secret=" + SECRET,
                        "jwt.issuer=alumni-uasz-user",
                        "service-token.issuer=alumni-uasz-internal",
                        "service-token.ttl=5m",
                        "company.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "company.connect-timeout=1s",
                        "company.read-timeout=1s",
                        "company.retry.max-retries=1",
                        "company.retry.delay-ms=10",
                        "company.retry.multiplier=2",
                        "company.retry.max-delay-ms=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(CompanyClient.class));
                });
    }

    @Test
    void litLaFicheAvecUnJetonDeService() {
        withClient(client -> {
            Optional<Company> company = client.find(COMPANY);

            assertThat(company).hasValueSatisfying(c -> {
                assertThat(c.getName()).isEqualTo("Gainde 2000");
                assertThat(c.getOwnerId()).isEqualTo(OWNER);
                assertThat(c.getIsPartner()).isTrue();
            });
        });

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()[0]).isEqualTo("/v1/internal/companies/" + COMPANY);
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build()
                .parseSignedClaims(received.getFirst()[1].substring("Bearer ".length()))
                .getPayload();
        assertThat(claims.getAudience()).containsExactly("alumni-student-company-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    void uneFicheInconnueDonneUnResultatVideSansRetry() {
        replies.add(404);

        withClient(client -> assertThat(client.find(COMPANY)).isEmpty());

        assertThat(received).hasSize(1);
    }

    @Test
    void unSeulRetrySurErreurTransitoireEnsuiteLErreurRemonte() {
        replies.add(503);

        withClient(client -> assertThatThrownBy(() -> client.find(COMPANY))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class));

        assertThat(received).hasSize(2); // 1 essai + 1 retry
    }
}
