package com.uasz.alumni.notificationmessaging.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.notificationmessaging.config.ApiPrefixConfig;
import com.uasz.alumni.notificationmessaging.config.SecurityConfig;
import com.uasz.alumni.notificationmessaging.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.notificationmessaging.jwt.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code POST /notifications/send} (ALUM-83, ADR-0010) : réservé aux jetons de service destinés à ce service
 * et aux administrateurs. Vraie chaîne de sécurité et vrai JwtService. L'émission elle-même relève d'ALUM-135 :
 * le contrôleur répond encore 501, ce qui prouve que la sécurité a laissé passer la requête.
 */
@WebMvcTest(controllers = NotificationsController.class, properties = {
        "security.api.prefix=/v1",
        "jwt.secret=" + SendNotificationSecurityTest.SECRET,
        "jwt.issuer=alumni-uasz-user",
        "service-token.issuer=alumni-uasz-internal",
        "spring.application.name=notification-messaging-service"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class, JwtService.class})
class SendNotificationSecurityTest {

    static final String SECRET = "dGVzdHMtb25seS1zZWNyZXQtMDEyMzQ1Njc4OS1hYmNkZWZnaGlqa2xtbm9w";
    static final String SELF = "notification-messaging-service";
    static final String BODY = """
            {"userId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","type":"APPLICATION_STATUS_CHANGED",
             "channel":"IN_APP","title":"Mise à jour","content":"Votre candidature est présélectionnée."}""";

    @Autowired MockMvc mvc;

    private static String token(String issuer, String subject, String audience, String role) {
        var builder = Jwts.builder().issuer(issuer).subject(subject).claim("role", role)
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(300)));
        if (audience != null) {
            builder.audience().add(audience);
        }
        return builder.signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256).compact();
    }

    private static String serviceToken(String audience) {
        return token("alumni-uasz-internal", "job-offer-service", audience, "SERVICE");
    }

    private static String userToken(String role) {
        return token("alumni-uasz-user", UUID.randomUUID().toString(), null, role);
    }

    private ResultActions send(String token) throws Exception {
        var req = post("/v1/notifications/send").contentType(MediaType.APPLICATION_JSON).content(BODY);
        if (token != null) {
            req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(req);
    }

    @Test
    void aServiceTokenForThisServiceReachesTheOperation() throws Exception {
        send(serviceToken(SELF)).andExpect(status().isNotImplemented());
    }

    @Test
    void anAdministratorReachesTheOperation() throws Exception {
        send(userToken("ADMINISTRATEUR")).andExpect(status().isNotImplemented());
    }

    @Test
    void anOrdinaryUserIs403() throws Exception {
        send(userToken("ALUMNI")).andExpect(status().isForbidden());
        send(userToken("ETUDIANT")).andExpect(status().isForbidden());
    }

    @Test
    void withoutTokenIs401() throws Exception {
        send(null).andExpect(status().isUnauthorized());
    }

    @Test
    void aServiceTokenForAnotherServiceIs401() throws Exception {
        send(serviceToken("alumni-student-company-service")).andExpect(status().isUnauthorized());
    }

    @Test
    void aServiceTokenHasNoAccessToTheUserApi() throws Exception {
        mvc.perform(get("/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken(SELF)))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersKeepAccessToTheirApi() throws Exception {
        // squelette encore non implémenté (ALUM-139) : la sécurité laisse passer, le contrôleur répond 501
        mvc.perform(get("/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("ETUDIANT")))
                .andExpect(status().isNotImplemented());
    }
}
