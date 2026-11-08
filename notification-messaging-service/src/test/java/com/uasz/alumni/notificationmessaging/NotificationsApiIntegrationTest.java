package com.uasz.alumni.notificationmessaging;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.uasz.alumni.notificationmessaging.repository.NotificationRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** POST /notifications/send réservé aux jetons de service (ADR-0010) ; lecture par le destinataire. */
@SpringBootTest
@ActiveProfiles("test")
class NotificationsApiIntegrationTest {

    static final SecretKeySpec KEY = new SecretKeySpec(
            Base64.getDecoder().decode("dGVzdHMtb25seS1zZWNyZXQtMDEyMzQ1Njc4OS1hYmNkZWZnaGlqa2xtbm9w"), "HmacSHA256");
    static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));

    @Autowired
    WebApplicationContext context;
    @Autowired
    NotificationRepository repository;

    MockMvc mvc;
    UUID student;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        repository.deleteAll();
        student = UUID.randomUUID();
    }

    // ---------- Émission ----------

    @Test
    void aServiceCanSendANotificationThatTheRecipientThenSees() throws Exception {
        send(serviceToken("job-offer-service", "notification-messaging-service"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(student.toString()))
                .andExpect(jsonPath("$.type").value("APPLICATION_STATUS_CHANGED"))
                .andExpect(jsonPath("$.isRead").value(false));

        mvc.perform(get("/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(userToken(student, "ETUDIANT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Votre candidature a été mise à jour"));
        mvc.perform(get("/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken(student, "ETUDIANT"))))
                .andExpect(jsonPath("$.unreadCount").value(1));

        // Un autre utilisateur ne voit rien
        mvc.perform(get("/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken(UUID.randomUUID(), "ETUDIANT"))))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anAdministratorCanSendANotification() throws Exception {
        send(userToken(UUID.randomUUID(), "ADMINISTRATEUR")).andExpect(status().isCreated());
    }

    @Test
    void aRegularUserCannotSendNotifications() throws Exception {
        send(userToken(UUID.randomUUID(), "ALUMNI")).andExpect(status().isForbidden());
    }

    @Test
    void aServiceTokenForAnotherAudienceIsRejected() throws Exception {
        send(serviceToken("job-offer-service", "user-service")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromAnUnknownIssuerIsRejected() throws Exception {
        send(token("pirate", "job-offer-service", "SERVICE", List.of("notification-messaging-service")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aServiceTokenSignedWithAnotherSecretIsRejected() throws Exception {
        var otherKey = new SecretKeySpec("une-autre-cle-secrete-de-32-octets-minimum!!".getBytes(), "HmacSHA256");
        var otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));
        Instant now = Instant.now();
        String forged = otherEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("alumni-uasz-internal").subject("job-offer-service")
                        .audience(List.of("notification-messaging-service")).issuedAt(now)
                        .expiresAt(now.plusSeconds(60)).claim("role", "SERVICE").build())).getTokenValue();
        send(forged).andExpect(status().isUnauthorized());
    }

    @Test
    void aServiceTokenCannotReadUserNotifications() throws Exception {
        mvc.perform(get("/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(serviceToken("job-offer-service", "notification-messaging-service"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPayloadIsRejected() throws Exception {
        mvc.perform(post("/v1/notifications/send")
                        .header(HttpHeaders.AUTHORIZATION, bearer(serviceToken("job-offer-service", "notification-messaging-service")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"sans destinataire\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Lecture ----------

    @Test
    void theRecipientCanMarkNotificationsAsRead() throws Exception {
        String json = send(serviceToken("job-offer-service", "notification-messaging-service"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");

        // Un autre utilisateur obtient 404 (on ne révèle pas l'existence de la notification)
        mvc.perform(patch("/v1/notifications/{id}/read", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken(UUID.randomUUID(), "ETUDIANT"))))
                .andExpect(status().isNotFound());

        mvc.perform(patch("/v1/notifications/{id}/read", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken(student, "ETUDIANT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        send(serviceToken("job-offer-service", "notification-messaging-service"));
        mvc.perform(patch("/v1/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken(student, "ETUDIANT"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken(student, "ETUDIANT"))))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ---------- Helpers ----------

    private ResultActions send(String token) throws Exception {
        String body = """
                {"userId":"%s","type":"APPLICATION_STATUS_CHANGED","channel":"IN_APP",
                 "title":"Votre candidature a été mise à jour","content":"Statut : présélectionnée",
                 "referenceId":"%s"}""".formatted(student, UUID.randomUUID());
        return mvc.perform(post("/v1/notifications/send")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String serviceToken(String caller, String audience) {
        return token("alumni-uasz-internal", caller, "SERVICE", List.of(audience));
    }

    private static String userToken(UUID userId, String role) {
        return token("alumni-uasz-user", userId.toString(), role, null);
    }

    private static String token(String issuer, String subject, String role, List<String> audience) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("role", role);
        if (audience != null) {
            claims.audience(audience);
        }
        return ENCODER.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
