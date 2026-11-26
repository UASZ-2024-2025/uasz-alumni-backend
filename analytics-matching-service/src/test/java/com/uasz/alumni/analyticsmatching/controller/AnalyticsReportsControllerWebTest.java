package com.uasz.alumni.analyticsmatching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.analyticsmatching.config.ApiPrefixConfig;
import com.uasz.alumni.analyticsmatching.config.SecurityConfig;
import com.uasz.alumni.analyticsmatching.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.analyticsmatching.jwt.JwtService;
import com.uasz.alumni.analyticsmatching.service.SkillsGapService;
import com.uasz.alumni.analyticsmatching.spi.model.SkillsGapResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste GET /v1/analytics/reports/skills-gap de bout en bout côté web : vraie chaîne de sécurité
 * (SecurityConfig + JwtAuthenticationFilter) et @PreAuthorize du contrôleur. SkillsGapService est simulé.
 */
@WebMvcTest(controllers = AnalyticsReportsController.class, properties = {
        "security.api.prefix=/v1",
        "security.cors.allowed-origins=http://localhost:4200"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class AnalyticsReportsControllerWebTest {

    private static final String URL = "/v1/analytics/reports/skills-gap";
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String ISSUER = "alumni-uasz-user";

    @TestConfiguration
    static class Config {
        /** Vrai JwtService : les jetons sont signés et validés comme en production. */
        @Bean
        JwtService jwtService() {
            return new JwtService(SECRET, ISSUER);
        }
    }

    @Autowired MockMvc mvc;

    @MockitoBean SkillsGapService skillsGapService;

    @Test
    void sansJeton_renvoie401() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    /** Le contrat réserve le rapport à l'ADMINISTRATEUR : tout autre rôle reçoit 403. */
    @ParameterizedTest
    @ValueSource(strings = {"ETUDIANT", "ALUMNI", "ENSEIGNANT"})
    void jetonSansDroit_renvoie403(String role) throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + token(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void jetonAdministrateur_renvoie200() throws Exception {
        when(skillsGapService.buildReport()).thenReturn(new SkillsGapResponse());

        mvc.perform(get(URL).header("Authorization", "Bearer " + token("ADMINISTRATEUR")))
                .andExpect(status().isOk());
    }

    /** Jeton identique à ceux émis par user-service ; le filtre doit créer l'autorité ROLE_&lt;role&gt;. */
    private static String token(String role) {
        return Jwts.builder().issuer(ISSUER).subject(UUID.randomUUID().toString())
                .claim("role", role).claim("email", "a@uasz.sn")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
    }
}
