package com.uasz.alumni.joboffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.uasz.alumni.joboffer.repository.ApplicationRepository;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.support.FakeNotificationService;
import com.uasz.alumni.joboffer.support.TestTokens;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Critère d'acceptation : un changement de statut de candidature déclenche une notification
 * (jeton de service, timeout, retry, non bloquant). notification-messaging-service est simulé.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationStatusNotificationIntegrationTest {

    static final FakeNotificationService NOTIFICATIONS;

    static {
        try {
            NOTIFICATIONS = new FakeNotificationService();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void notificationUrl(DynamicPropertyRegistry registry) {
        registry.add("notification.base-url", NOTIFICATIONS::baseUrl);
    }

    @AfterAll
    static void stopFake() {
        NOTIFICATIONS.close();
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    ApplicationRepository applications;

    MockMvc mvc;
    UUID recruiter;
    UUID student;
    String applicationId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        NOTIFICATIONS.reset();
        recruiter = UUID.randomUUID();
        student = UUID.randomUUID();
        String offerId = createOffer(recruiter);
        applicationId = apply(student, offerId);
    }

    // ---------- Flux nominal ----------

    @Test
    void changingStatusSendsANotificationToTheStudentWithAServiceToken() throws Exception {
        changeStatus(recruiter, "ALUMNI", "SHORTLISTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHORTLISTED"));

        NOTIFICATIONS.awaitCalls(1, Duration.ofSeconds(5));
        FakeNotificationService.Call call = NOTIFICATIONS.calls().getFirst();

        assertThat(call.body())
                .contains("\"userId\":\"" + student + "\"")
                .contains("\"type\":\"APPLICATION_STATUS_CHANGED\"")
                .contains("\"channel\":\"IN_APP\"")
                .contains("\"referenceId\":\"" + applicationId + "\"")
                .contains("présélectionnée");

        // Jeton de service : signé, émetteur interne, audience = service de notification, rôle SERVICE
        assertThat(call.authorization()).startsWith("Bearer ");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(TestTokens.KEY).macAlgorithm(MacAlgorithm.HS256).build();
        Jwt token = decoder.decode(call.authorization().substring("Bearer ".length()));
        assertThat(token.getClaimAsString("iss")).isEqualTo("alumni-uasz-internal");
        assertThat(token.getSubject()).isEqualTo("job-offer-service");
        assertThat(token.getAudience()).containsExactly("notification-messaging-service");
        assertThat(token.getClaimAsString("role")).isEqualTo("SERVICE");
        assertThat(token.getExpiresAt()).isBefore(token.getIssuedAt().plus(Duration.ofMinutes(6)));
    }

    @Test
    void unchangedStatusSendsNoNotification() throws Exception {
        changeStatus(recruiter, "ALUMNI", "SUBMITTED").andExpect(status().isOk());

        Thread.sleep(300);
        assertThat(NOTIFICATIONS.calls()).isEmpty();
    }

    // ---------- Pannes du service de notification ----------

    @Test
    void transientErrorsAreRetriedUntilSuccess() throws Exception {
        NOTIFICATIONS.respondWith(503, 503);

        changeStatus(recruiter, "ALUMNI", "UNDER_REVIEW").andExpect(status().isOk());

        NOTIFICATIONS.awaitCalls(3, Duration.ofSeconds(5));
        Thread.sleep(300);
        assertThat(NOTIFICATIONS.calls()).hasSize(3);
    }

    @Test
    void persistentOutageDoesNotBreakTheStatusChange() throws Exception {
        NOTIFICATIONS.alwaysRespond(500);

        changeStatus(recruiter, "ALUMNI", "REJECTED").andExpect(status().isOk());

        // 1 essai + 3 retries, puis abandon journalisé
        NOTIFICATIONS.awaitCalls(4, Duration.ofSeconds(5));
        Thread.sleep(300);
        assertThat(NOTIFICATIONS.calls()).hasSize(4);
        assertThat(applications.findById(UUID.fromString(applicationId)).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    void clientErrorsAreNotRetried() throws Exception {
        NOTIFICATIONS.alwaysRespond(400);

        changeStatus(recruiter, "ALUMNI", "ACCEPTED").andExpect(status().isOk());

        NOTIFICATIONS.awaitCalls(1, Duration.ofSeconds(5));
        Thread.sleep(400);
        assertThat(NOTIFICATIONS.calls()).hasSize(1);
    }

    @Test
    void slowNotificationServiceTimesOutWithoutDelayingTheRecruiter() throws Exception {
        NOTIFICATIONS.delayResponses(Duration.ofSeconds(2)); // > read-timeout (500 ms)

        long start = System.nanoTime();
        changeStatus(recruiter, "ALUMNI", "SHORTLISTED").andExpect(status().isOk());
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(500));

        // chaque tentative expire, puis est rejouée : 4 tentatives au total
        NOTIFICATIONS.awaitCalls(4, Duration.ofSeconds(10));
    }

    // ---------- Autorisations ----------

    @Test
    void onlyTheOfferPublisherOrAnAdminCanChangeTheStatus() throws Exception {
        changeStatus(UUID.randomUUID(), "ALUMNI", "ACCEPTED").andExpect(status().isForbidden());
        changeStatus(student, "ETUDIANT", "ACCEPTED").andExpect(status().isForbidden());
        changeStatus(UUID.randomUUID(), "ADMINISTRATEUR", "UNDER_REVIEW").andExpect(status().isOk());
    }

    @Test
    void requestsWithoutValidUserTokenAreRejected() throws Exception {
        mvc.perform(patch("/v1/applications/{id}/status", applicationId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isUnauthorized());

        // Un jeton de service n'est pas accepté sur l'API utilisateur
        String serviceToken = TestTokens.token("alumni-uasz-internal", "analytics-matching-service", "SERVICE");
        mvc.perform(patch("/v1/applications/{id}/status", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(serviceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownApplicationReturns404() throws Exception {
        mvc.perform(patch("/v1/applications/{id}/status", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.user(recruiter, "ALUMNI")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }

    // ---------- Candidature ----------

    @Test
    void aStudentCannotApplyTwiceToTheSameOffer() throws Exception {
        String offerId = createOffer(recruiter);
        apply(student, offerId);
        mvc.perform(post("/v1/offers/{id}/applications", offerId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.user(student, "ETUDIANT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
    }

    @Test
    void offerDetailsArePublic() throws Exception {
        String offerId = createOffer(recruiter);
        mvc.perform(get("/v1/offers/{id}", offerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredSkills[0]").value("Java"));
    }

    // ---------- Helpers ----------

    private String createOffer(UUID publisher) throws Exception {
        String body = """
                {"title":"Stage développeur Java","type":"INTERNSHIP","description":"Stage de 6 mois",
                 "companyId":"%s","location":"Dakar","requiredSkills":["Java","Spring Boot"]}
                """.formatted(UUID.randomUUID());
        String json = mvc.perform(post("/v1/offers")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.user(publisher, "ALUMNI")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String apply(UUID studentUser, String offerId) throws Exception {
        String json = mvc.perform(post("/v1/offers/{id}/applications", offerId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.user(studentUser, "ETUDIANT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + UUID.randomUUID() + "\",\"coverLetter\":\"Motivé\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(UUID user, String role, String newStatus)
            throws Exception {
        return mvc.perform(patch("/v1/applications/{id}/status", applicationId)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.user(user, role)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + newStatus + "\"}"));
    }
}
