package com.uasz.alumni.analyticsmatching.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Clients réels contre de faux services : JWT propagé, pagination des offres, étudiant inconnu (ALUM-180). */
class MatchingClientsTest {

    private static final String CALLER = "Bearer jeton-appelant";
    private static final UUID KNOWN = UUID.randomUUID();

    private HttpServer server;
    private final List<String[]> received = new CopyOnWriteArrayList<>(); // {chemin?requête, Authorization}

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sc/v1/students", exchange -> {
            record(exchange);
            boolean known = exchange.getRequestURI().getPath().endsWith(KNOWN.toString());
            reply(exchange, known ? 200 : 404, known
                    ? "{\"id\":\"" + KNOWN + "\",\"userId\":\"" + KNOWN + "\",\"skills\":[\"Java\"]}"
                    : "{\"code\":\"STUDENT_NOT_FOUND\",\"message\":\"x\"}");
        });
        server.createContext("/sc2/v1/students", exchange -> {
            record(exchange);
            boolean firstPage = exchange.getRequestURI().getQuery().contains("page=0");
            String content = firstPage
                    ? "[{\"id\":\"" + UUID.randomUUID() + "\",\"firstName\":\"A\"}]"
                    : "[{\"id\":\"" + UUID.randomUUID() + "\",\"firstName\":\"B\"}]";
            reply(exchange, 200, "{\"content\":" + content + ",\"totalElements\":2,\"page\":0,\"size\":100}");
        });
        server.createContext("/jo/v1/offers/", exchange -> {
            record(exchange);
            boolean known = exchange.getRequestURI().getPath().endsWith(KNOWN.toString());
            reply(exchange, known ? 200 : 404, known
                    ? "{\"id\":\"" + KNOWN + "\",\"title\":\"Dev\",\"publisherId\":\"" + KNOWN + "\"}"
                    : "{\"code\":\"OFFER_NOT_FOUND\",\"message\":\"x\"}");
        });
        server.createContext("/jo/v1/offers", exchange -> {
            record(exchange);
            boolean firstPage = exchange.getRequestURI().getQuery().contains("page=0");
            String content = firstPage
                    ? "[{\"id\":\"" + UUID.randomUUID() + "\",\"title\":\"A\"},{\"id\":\"" + UUID.randomUUID() + "\",\"title\":\"B\"}]"
                    : "[{\"id\":\"" + UUID.randomUUID() + "\",\"title\":\"C\"}]";
            reply(exchange, 200, "{\"content\":" + content + ",\"totalElements\":3,\"page\":0,\"size\":100}");
        });
        server.start();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", CALLER);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void stop() {
        RequestContextHolder.resetRequestAttributes();
        server.stop(0);
    }

    private void record(HttpExchange exchange) {
        received.add(new String[] {exchange.getRequestURI().getPath() + "?" + exchange.getRequestURI().getQuery(),
                exchange.getRequestHeaders().getFirst("Authorization")});
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String base(String prefix) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + prefix;
    }

    @Test
    void profilEtudiantLuAvecLeJetonDeLAppelant() {
        StudentProfilesClient client = new StudentProfilesClient(base("/sc/v1"), Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(client.find(KNOWN)).hasValueSatisfying(p -> assertThat(p.getSkills()).containsExactly("Java"));
        assertThat(client.find(UUID.randomUUID())).isEmpty();
        assertThat(received).allSatisfy(r -> assertThat(r[1]).isEqualTo(CALLER));
    }

    @Test
    void offresOuvertesLuesPageParPage() {
        JobOffersClient client = new JobOffersClient(base("/jo/v1"), Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(client.listOpen()).extracting(o -> o.getTitle()).containsExactly("A", "B", "C");
        assertThat(received).hasSize(2);
        assertThat(received.getFirst()[0]).contains("status=OPEN").contains("page=0").contains("size=100");
        assertThat(received).allSatisfy(r -> assertThat(r[1]).isEqualTo(CALLER));
    }

    @Test
    void tousLesProfilsLusPageParPage() {
        StudentProfilesClient client = new StudentProfilesClient(base("/sc2/v1"), Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(client.listAll()).extracting(p -> p.getFirstName()).containsExactly("A", "B");
        assertThat(received).hasSize(2);
        assertThat(received).allSatisfy(r -> assertThat(r[1]).isEqualTo(CALLER));
    }

    @Test
    void uneOffreLueAvecLeJetonDeLAppelant() {
        JobOffersClient client = new JobOffersClient(base("/jo/v1"), Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(client.find(KNOWN)).hasValueSatisfying(o -> assertThat(o.getPublisherId()).isEqualTo(KNOWN));
        assertThat(client.find(UUID.randomUUID())).isEmpty();
        assertThat(received).allSatisfy(r -> assertThat(r[1]).isEqualTo(CALLER));
    }
}
