package com.uasz.alumni.alumnistudentcompany;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
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

/**
 * {@code POST /internal/profiles} (ALUM-83) : réservé aux jetons de service destinés à ce service
 * (ADR-0010), et le reste de l'API reste réservé aux utilisateurs.
 */
@SpringBootTest
@ActiveProfiles("test")
class InternalProfilesApiIntegrationTest {

    static final SecretKeySpec KEY = new SecretKeySpec(
            Base64.getDecoder().decode("dGVzdHMtb25seS1zZWNyZXQtMDEyMzQ1Njc4OS1hYmNkZWZnaGlqa2xtbm9w"), "HmacSHA256");
    static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));
    static final String SELF = "alumni-student-company-service";

    static final String VALID_BODY = """
            {"userId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","profileType":"ETUDIANT",
             "firstName":"Awa","lastName":"Diop","email":"awa.diop@uasz.sn"}""";

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---------- /internal/profiles ----------

    @Test
    void aServiceTokenReachesTheOperation() throws Exception {
        // la création effective attend la fusion des tables de profils : 501 en attendant
        provision(serviceToken("user-service", SELF), VALID_BODY)
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    @Test
    void anInvalidRequestIsRejectedBeforeAnything() throws Exception {
        provision(serviceToken("user-service", SELF), "{\"profileType\":\"ETUDIANT\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(post("/v1/internal/profiles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUserTokenIsForbidden() throws Exception {
        provision(userToken(UUID.randomUUID(), "ADMINISTRATEUR"), VALID_BODY).andExpect(status().isForbidden());
        provision(userToken(UUID.randomUUID(), "ETUDIANT"), VALID_BODY).andExpect(status().isForbidden());
    }

    @Test
    void aServiceTokenForAnotherServiceIsRejected() throws Exception {
        provision(serviceToken("user-service", "notification-messaging-service"), VALID_BODY)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromAnUnknownIssuerIsRejected() throws Exception {
        String forged = token("pirate", "user-service", List.of(SELF), "SERVICE");
        provision(forged, VALID_BODY).andExpect(status().isUnauthorized());
    }

    // ---------- reste de l'API ----------

    @Test
    void aServiceTokenHasNoAccessToTheUserApi() throws Exception {
        mvc.perform(get("/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(serviceToken("user-service", SELF))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserTokenStillReachesTheUserApi() throws Exception {
        // squelette encore non implémenté : la sécurité laisse passer, le contrôleur répond 501
        mvc.perform(get("/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(userToken(UUID.randomUUID(), "ALUMNI"))))
                .andExpect(status().isNotImplemented());
    }

    // ---------- outils ----------

    private ResultActions provision(String token, String body) throws Exception {
        return mvc.perform(post("/v1/internal/profiles")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String userToken(UUID userId, String role) {
        return token("alumni-uasz-user", userId.toString(), null, role);
    }

    private static String serviceToken(String caller, String audience) {
        return token("alumni-uasz-internal", caller, List.of(audience), "SERVICE");
    }

    private static String token(String issuer, String subject, List<String> audience, String role) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(subject).issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("role", role);
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
