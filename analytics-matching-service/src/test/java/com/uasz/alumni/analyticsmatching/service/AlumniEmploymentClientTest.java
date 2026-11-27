package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniEmploymentRecord;
import com.uasz.alumni.analyticsmatching.jwt.ServiceTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
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
import org.springframework.web.client.HttpServerErrorException;

/** Client réel (proxy {@code @Retryable} compris) contre un faux alumni-student-company-service (ALUM-183). */
class AlumniEmploymentClientTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final String BODY = """
            [{"graduationYear":2024,"specialty":"Génie Logiciel","employed":true,
              "experienceStartDates":["2024-09-15","2025-01-01"]}]""";

    private HttpServer server;
    private final ConcurrentLinkedQueue<Integer> replies = new ConcurrentLinkedQueue<>();
    private final List<String[]> received = new CopyOnWriteArrayList<>(); // {chemin, requête brute, Authorization}
    private volatile int last = 200;

    @Configuration
    @EnableResilientMethods
    static class ResilienceOnly {
    }

    @BeforeEach
    void startFakeService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/internal/alumni/employment", exchange -> {
            received.add(new String[] {exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Authorization")});
            Integer next = replies.poll();
            int status = next == null ? last : next;
            last = status;
            String json = status == 200 ? BODY : "{\"code\":\"X\",\"message\":\"x\"}";
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

    private void withClient(Consumer<AlumniEmploymentClient> test) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(ResilienceOnly.class, AlumniEmploymentClient.class, ServiceTokenProvider.class)
                .withPropertyValues(
                        "spring.application.name=analytics-matching-service",
                        "jwt.secret=" + SECRET,
                        "jwt.issuer=alumni-uasz-user",
                        "service-token.issuer=alumni-uasz-internal",
                        "service-token.ttl=5m",
                        "student-company.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "student-company.connect-timeout=1s",
                        "student-company.read-timeout=1s",
                        "student-company.retry.max-retries=1",
                        "student-company.retry.delay-ms=10",
                        "student-company.retry.multiplier=2",
                        "student-company.retry.max-delay-ms=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(AlumniEmploymentClient.class));
                });
    }

    @Test
    void litLesDiplomesAvecUnJetonDeService() {
        withClient(client -> {
            List<AlumniEmploymentRecord> records = client.list(2024, "Génie Logiciel");

            assertThat(records).singleElement().satisfies(r -> {
                assertThat(r.getGraduationYear()).isEqualTo(2024);
                assertThat(r.getSpecialty()).isEqualTo("Génie Logiciel");
                assertThat(r.getEmployed()).isTrue();
                assertThat(r.getExperienceStartDates())
                        .containsExactly(LocalDate.of(2024, 9, 15), LocalDate.of(2025, 1, 1));
            });
        });

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()[0]).isEqualTo("/v1/internal/alumni/employment");
        assertThat(received.getFirst()[1]).isEqualTo("graduationYear=2024&specialty=G%C3%A9nie%20Logiciel");
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build()
                .parseSignedClaims(received.getFirst()[2].substring("Bearer ".length()))
                .getPayload();
        assertThat(claims.getAudience()).containsExactly("alumni-student-company-service");
        assertThat(claims.getSubject()).isEqualTo("analytics-matching-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    void encodeLesCaracteresSpeciauxDuFiltre() {
        withClient(client -> client.list(null, "R&D+IA"));

        assertThat(received.getFirst()[1]).isEqualTo("specialty=R%26D%2BIA");
    }

    @Test
    void sansFiltreAucunParametreNEstEnvoye() {
        withClient(client -> client.list(null, null));

        assertThat(received.getFirst()[1]).isNull();
    }

    @Test
    void unSeulRetrySurErreurTransitoire() {
        replies.add(503);

        withClient(client -> assertThat(client.list(null, null)).hasSize(1));

        assertThat(received).hasSize(2); // 1 essai en échec + 1 retry réussi
    }

    @Test
    void apresLeRetryLErreurRemonte() {
        replies.add(503);
        replies.add(503);

        withClient(client -> assertThatThrownBy(() -> client.list(null, null))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class));

        assertThat(received).hasSize(2);
    }
}
