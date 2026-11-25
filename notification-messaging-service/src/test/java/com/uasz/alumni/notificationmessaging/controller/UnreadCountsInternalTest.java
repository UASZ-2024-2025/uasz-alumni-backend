package com.uasz.alumni.notificationmessaging.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.notificationmessaging.config.ApiPrefixConfig;
import com.uasz.alumni.notificationmessaging.config.SecurityConfig;
import com.uasz.alumni.notificationmessaging.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.notificationmessaging.jwt.JwtService;
import com.uasz.alumni.notificationmessaging.service.GroupService;
import com.uasz.alumni.notificationmessaging.service.NotificationCenterService;
import com.uasz.alumni.notificationmessaging.service.PrivateMessagingService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET /internal/users/{userId}/unread-counts} (ALUM-243, ADR-0010) : réservé aux jetons de service
 * destinés à ce service. Vraie chaîne de sécurité et vrai JwtService ; les compteurs sont simulés.
 */
@WebMvcTest(controllers = InternalController.class, properties = {
    "security.api.prefix=/v1",
    "jwt.secret=" + UnreadCountsInternalTest.SECRET,
    "jwt.issuer=alumni-uasz-user",
    "service-token.issuer=alumni-uasz-internal",
    "spring.application.name=notification-messaging-service"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class, JwtService.class})
class UnreadCountsInternalTest {

    static final String SECRET = "dGVzdHMtb25seS1zZWNyZXQtMDEyMzQ1Njc4OS1hYmNkZWZnaGlqa2xtbm9w";
    static final String SELF = "notification-messaging-service";
    static final UUID USER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    static final String URL = "/v1/internal/users/" + USER + "/unread-counts";

    @Autowired MockMvc mvc;
    @MockitoBean NotificationCenterService center;
    @MockitoBean PrivateMessagingService privateMessages;
    @MockitoBean GroupService groups;

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

    private ResultActions call(String token) throws Exception {
        var req = get(URL);
        if (token != null) {
            req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(req);
    }

    @Test
    void aServiceTokenGetsTheCounts() throws Exception {
        when(center.unreadCount(USER)).thenReturn(3L);
        when(privateMessages.unreadTotal(USER)).thenReturn(2L);
        when(groups.unreadTotal(USER)).thenReturn(3L);

        call(serviceToken(SELF)).andExpect(status().isOk())
            .andExpect(jsonPath("$.notifications").value(3))
            .andExpect(jsonPath("$.messages").value(5));
    }

    @Test
    void anUnknownUserHasZeroEverywhere() throws Exception {
        call(serviceToken(SELF)).andExpect(status().isOk())
            .andExpect(jsonPath("$.notifications").value(0))
            .andExpect(jsonPath("$.messages").value(0));
    }

    @Test
    void usersAreRefusedEvenAdministrators() throws Exception {
        call(userToken("ETUDIANT")).andExpect(status().isForbidden());
        call(userToken("ALUMNI")).andExpect(status().isForbidden());
        call(userToken("ADMINISTRATEUR")).andExpect(status().isForbidden());
    }

    @Test
    void withoutTokenIs401() throws Exception {
        call(null).andExpect(status().isUnauthorized());
    }

    @Test
    void aServiceTokenForAnotherServiceIs401() throws Exception {
        call(serviceToken("alumni-student-company-service")).andExpect(status().isUnauthorized());
    }
}
