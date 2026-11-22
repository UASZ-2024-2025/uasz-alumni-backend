package com.uasz.alumni.notificationmessaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "SERVER_PORT=0",
                "FORWARD_HEADERS_STRATEGY=none",
                "API_PREFIX=/v1",
                "CORS_ALLOWED_ORIGINS=http://localhost:4200",
                "JWT_SECRET=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=",
                "JWT_ISSUER=alumni-uasz-user"
        })
class PrivateMessagingAccessTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=";
    private static final String ISSUER = "alumni-uasz-user";

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

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // ---------- outils ----------

    private String token(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(ISSUER)
                .claim("role", "ETUDIANT")
                .claim("email", userId + "@test.sn")
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .compact();
    }

    private HttpResponse<String> call(String method, String path, UUID asUser, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1" + path))
                .header("Content-Type", "application/json");
        if (asUser != null) {
            b.header("Authorization", "Bearer " + token(asUser));
        }
        HttpRequest.BodyPublisher body = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json);
        return http.send(b.method(method, body).build(), HttpResponse.BodyHandlers.ofString());
    }

    private UUID sendMessage(UUID from, UUID to, String text) throws Exception {
        HttpResponse<String> r = call("POST", "/messages/conversations/" + to, from,
                "{\"content\":\"" + text + "\"}");
        assertThat(r.statusCode()).isEqualTo(201);
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"").matcher(r.body());
        assertThat(m.find()).isTrue();
        return UUID.fromString(m.group(1));
    }

    // ---------- tests ----------

    @Test
    void sansJeton_401() throws Exception {
        assertThat(call("GET", "/messages/conversations", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void roleNonAutorise_403EtPas401() throws Exception {
        // Vrai serveur HTTP (pas MockMvc) : un refus @PreAuthorize passait par /error et repartait en 401
        String entreprise = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .claim("role", "ENTREPRISE")
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .compact();
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/messages/groups"))
                .header("Authorization", "Bearer " + entreprise).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.body()).contains("FORBIDDEN");
    }

    @Test
    void messageVide_400() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThat(call("POST", "/messages/conversations/" + b, a, "{\"content\":\"\"}").statusCode())
                .isEqualTo(400);
    }

    @Test
    void ecrireASoiMeme_400() throws Exception {
        UUID a = UUID.randomUUID();
        assertThat(call("POST", "/messages/conversations/" + a, a, "{\"content\":\"moi\"}").statusCode())
                .isEqualTo(400);
    }

    @Test
    void envoiPuisLecture_lAutreRecoitLeMessage() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        sendMessage(a, b, "Bonjour B");

        HttpResponse<String> r = call("GET", "/messages/conversations/" + a, b, null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("Bonjour B");
    }

    @Test
    void tiersNePeutPasLireLaConversation() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID tiers = UUID.randomUUID();
        sendMessage(a, b, "Message prive entre A et B");

        HttpResponse<String> r = call("GET", "/messages/conversations/" + a, tiers, null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).doesNotContain("Message prive entre A et B");
    }

    @Test
    void supprimerLeMessageDUnAutre_404() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID messageId = sendMessage(a, b, "A ecrit a B");

        // 404 comme pour un message inexistant : le contrat ne prévoit pas de 403 sur cette route
        assertThat(call("DELETE", "/messages/" + messageId, b, null).statusCode()).isEqualTo(404);
        assertThat(call("DELETE", "/messages/" + messageId, a, null).statusCode()).isEqualTo(204);
    }

    @Test
    void supprimerUnMessageInexistant_404() throws Exception {
        UUID a = UUID.randomUUID();
        assertThat(call("DELETE", "/messages/" + UUID.randomUUID(), a, null).statusCode()).isEqualTo(404);
    }

    @Test
    void historique_plusRecentEnPremier() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        sendMessage(a, b, "Premier message");
        Thread.sleep(50);
        sendMessage(a, b, "Second message");

        HttpResponse<String> r = call("GET", "/messages/conversations/" + a, b, null);
        assertThat(r.statusCode()).isEqualTo(200);
        int second = r.body().indexOf("Second message");
        int premier = r.body().indexOf("Premier message");
        assertThat(second).isGreaterThanOrEqualTo(0);
        assertThat(premier).isGreaterThan(second);
    }
}
