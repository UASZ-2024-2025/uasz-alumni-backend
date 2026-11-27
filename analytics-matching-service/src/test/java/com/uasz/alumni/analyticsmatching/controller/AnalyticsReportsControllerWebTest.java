package com.uasz.alumni.analyticsmatching.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.analyticsmatching.config.ApiPrefixConfig;
import com.uasz.alumni.analyticsmatching.config.SecurityConfig;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.analyticsmatching.jwt.JwtService;
import com.uasz.alumni.analyticsmatching.service.InsertionReportService;
import com.uasz.alumni.analyticsmatching.service.SkillsGapService;
import com.uasz.alumni.analyticsmatching.spi.model.InsertionReportItem;
import com.uasz.alumni.analyticsmatching.spi.model.InsertionReportResponse;
import com.uasz.alumni.analyticsmatching.spi.model.SkillsGapResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste GET /v1/analytics/reports/skills-gap et /insertion de bout en bout côté web : vraie chaîne de sécurité
 * (SecurityConfig + JwtAuthenticationFilter) et @PreAuthorize du contrôleur. Les services métier sont simulés.
 */
@WebMvcTest(controllers = AnalyticsReportsController.class, properties = {
        "security.api.prefix=/v1",
        "security.cors.allowed-origins=http://localhost:4200"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class AnalyticsReportsControllerWebTest {

    private static final String URL = "/v1/analytics/reports/skills-gap";
    private static final String INSERTION_URL = "/v1/analytics/reports/insertion";
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
    @MockitoBean InsertionReportService insertionReportService;

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

    // ---------- rapport d'insertion (ALUM-183) ----------

    @Test
    void insertion_sansJeton_renvoie401() throws Exception {
        mvc.perform(get(INSERTION_URL))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(insertionReportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ETUDIANT", "ALUMNI", "ENSEIGNANT", "ENTREPRISE"})
    void insertion_jetonSansDroit_renvoie403(String role) throws Exception {
        mvc.perform(get(INSERTION_URL).header("Authorization", "Bearer " + token(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(insertionReportService);
    }

    @Test
    void insertion_administrateur_renvoie200_et_transmetLesFiltres() throws Exception {
        InsertionReportItem item = new InsertionReportItem();
        item.setGraduationYear(2024);
        item.setSpecialty("Génie Logiciel");
        item.setTotalGraduates(4);
        item.setEmployedCount(3);
        item.setInsertionRate(75.0f);
        item.setAverageMonthsToFirstJob(3.7f);
        InsertionReportResponse report = new InsertionReportResponse();
        report.setOverallInsertionRate(75.0f);
        report.setReportData(List.of(item));
        when(insertionReportService.buildReport(2024, "Génie Logiciel")).thenReturn(report);

        mvc.perform(get(INSERTION_URL).param("graduationYear", "2024").param("specialty", "Génie Logiciel")
                        .header("Authorization", "Bearer " + token("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallInsertionRate").value(75.0))
                .andExpect(jsonPath("$.reportData[0].graduationYear").value(2024))
                .andExpect(jsonPath("$.reportData[0].specialty").value("Génie Logiciel"))
                .andExpect(jsonPath("$.reportData[0].totalGraduates").value(4))
                .andExpect(jsonPath("$.reportData[0].employedCount").value(3))
                .andExpect(jsonPath("$.reportData[0].insertionRate").value(75.0))
                .andExpect(jsonPath("$.reportData[0].averageMonthsToFirstJob").value(3.7));
    }

    @Test
    void insertion_filtreInvalide_renvoie400AuFormatErrorResponse() throws Exception {
        when(insertionReportService.buildReport(1800, null)).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "INVALID_GRADUATION_YEAR", "L'année de diplôme doit être comprise entre 1990 et 2100."));

        mvc.perform(get(INSERTION_URL).param("graduationYear", "1800")
                        .header("Authorization", "Bearer " + token("ADMINISTRATEUR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GRADUATION_YEAR"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void insertion_serviceAlumniIndisponible_renvoie503() throws Exception {
        when(insertionReportService.buildReport(null, null)).thenThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "ALUMNI_SERVICE_UNAVAILABLE", "Données indisponibles"));

        mvc.perform(get(INSERTION_URL).header("Authorization", "Bearer " + token("ADMINISTRATEUR")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ALUMNI_SERVICE_UNAVAILABLE"));
    }

    @Test
    void insertion_anneeNonNumerique_renvoie400() throws Exception {
        mvc.perform(get(INSERTION_URL).param("graduationYear", "abc")
                        .header("Authorization", "Bearer " + token("ADMINISTRATEUR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(insertionReportService);
    }

    /** Jeton identique à ceux émis par user-service ; le filtre doit créer l'autorité ROLE_&lt;role&gt;. */
    private static String token(String role) {
        return Jwts.builder().issuer(ISSUER).subject(UUID.randomUUID().toString())
                .claim("role", role).claim("email", "a@uasz.sn")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
    }
}
