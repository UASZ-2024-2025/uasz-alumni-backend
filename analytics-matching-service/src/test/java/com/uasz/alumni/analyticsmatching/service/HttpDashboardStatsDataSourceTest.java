package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.jwt.ServiceTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

/** Client réel contre de faux alumni-student-company-service et job-offer-service (ALUM-182). */
class HttpDashboardStatsDataSourceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());

    private HttpServer server;
    private final List<String[]> received = new CopyOnWriteArrayList<>(); // {chemin?requête, Authorization}
    private volatile int statsStatus = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sc/v1/internal/stats", exchange -> {
            received.add(new String[] {exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization")});
            reply(exchange, statsStatus, statsStatus == 200
                    ? "{\"alumniCount\":120,\"employedAlumniCount\":96,\"studentCount\":80,\"companyCount\":25}"
                    : "{\"code\":\"X\",\"message\":\"x\"}");
        });
        server.createContext("/jo/v1/offers", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            received.add(new String[] {exchange.getRequestURI().getPath() + "?" + query, null});
            int total = query.contains("type=INTERNSHIP") ? 7 : 4;
            reply(exchange, 200, "{\"content\":[],\"totalElements\":" + total + ",\"page\":0,\"size\":1}");
        });
        server.start();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void withSource(Consumer<DashboardStatsDataSource> test) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(HttpDashboardStatsDataSource.class, ServiceTokenProvider.class)
                .withPropertyValues(
                        "spring.application.name=analytics-matching-service",
                        "jwt.secret=" + SECRET,
                        "jwt.issuer=alumni-uasz-user",
                        "service-token.issuer=alumni-uasz-internal",
                        "service-token.ttl=5m",
                        "clients.student-company.base-url=" + base + "/sc/v1",
                        "clients.job-offer.base-url=" + base + "/jo/v1",
                        "clients.connect-timeout=1s",
                        "clients.read-timeout=1s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(DashboardStatsDataSource.class));
                });
    }

    @Test
    void litLesCompteursAvecUnJetonDeServiceEtLesOffresParType() {
        withSource(source -> assertThat(source.fetchCounts())
                .isEqualTo(new DashboardStatsDataSource.PlatformCounts(120, 96, 80, 25, 7, 4)));

        String[] stats = received.stream().filter(r -> r[0].endsWith("/internal/stats")).findFirst().orElseThrow();
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build()
                .parseSignedClaims(stats[1].substring("Bearer ".length()))
                .getPayload();
        assertThat(claims.getAudience()).containsExactly("alumni-student-company-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
        assertThat(received).extracting(r -> r[0])
                .anyMatch(path -> path.contains("type=INTERNSHIP"))
                .anyMatch(path -> path.contains("type=JOB"));
    }

    @Test
    void unServiceSourceEnErreurDonne503() {
        statsStatus = 500;

        withSource(source -> assertThatThrownBy(source::fetchCounts)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)));
    }
}
