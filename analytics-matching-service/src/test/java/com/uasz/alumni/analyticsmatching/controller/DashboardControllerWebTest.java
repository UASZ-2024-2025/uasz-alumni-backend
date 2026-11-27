package com.uasz.alumni.analyticsmatching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.analyticsmatching.config.ApiPrefixConfig;
import com.uasz.alumni.analyticsmatching.config.SecurityConfig;
import com.uasz.alumni.analyticsmatching.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.analyticsmatching.jwt.JwtService;
import com.uasz.alumni.analyticsmatching.service.DashboardService;
import com.uasz.alumni.analyticsmatching.spi.model.DashboardStats;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

@WebMvcTest(controllers = DashboardController.class, properties = {
        "security.api.prefix=/v1",
        "security.cors.allowed-origins=http://localhost:4200"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class DashboardControllerWebTest {

    private static final String URL = "/v1/analytics/dashboard/stats";
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String ISSUER = "alumni-uasz-user";

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService(SECRET, ISSUER);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean DashboardService dashboardService;

    @Test
    void sansJeton_renvoie401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ETUDIANT", "ALUMNI", "ENSEIGNANT", "ENTREPRISE"})
    void nonAdministrateur_renvoie403(String role) throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + token(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrateur_renvoie200EtChiffres() throws Exception {
        DashboardStats stats = new DashboardStats()
                .alumniCount(350)
                .studentCount(120)
                .companyCount(45)
                .insertionRate(88.5f)
                .internshipCount(85)
                .jobOfferCount(140)
                .updatedAt(OffsetDateTime.of(2026, 11, 24, 12, 0, 0, 0, ZoneOffset.UTC));
        when(dashboardService.getStats()).thenReturn(stats);

        mvc.perform(get(URL).header("Authorization", "Bearer " + token("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alumniCount").value(350))
                .andExpect(jsonPath("$.studentCount").value(120))
                .andExpect(jsonPath("$.companyCount").value(45))
                .andExpect(jsonPath("$.insertionRate").value(88.5))
                .andExpect(jsonPath("$.internshipCount").value(85))
                .andExpect(jsonPath("$.jobOfferCount").value(140))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    private static String token(String role) {
        return Jwts.builder().issuer(ISSUER).subject(UUID.randomUUID().toString())
                .claim("role", role).claim("email", "admin@uasz.sn")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
    }
}
