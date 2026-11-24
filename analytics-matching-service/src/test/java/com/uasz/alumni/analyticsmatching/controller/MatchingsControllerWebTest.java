package com.uasz.alumni.analyticsmatching.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.analyticsmatching.config.ApiPrefixConfig;
import com.uasz.alumni.analyticsmatching.config.SecurityConfig;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.analyticsmatching.jwt.JwtService;
import com.uasz.alumni.analyticsmatching.service.MatchingService;
import com.uasz.alumni.analyticsmatching.spi.model.OfferMatchResult;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MatchingsController.class, properties = {
        "security.api.prefix=/v1",
        "security.cors.allowed-origins=http://localhost:4200"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class MatchingsControllerWebTest {

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
    @MockitoBean MatchingService matchingService;

    @Test
    void sansJeton_renvoie401() throws Exception {
        mvc.perform(get("/v1/analytics/matchings/students/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void etudiantInexistant_renvoie404() throws Exception {
        UUID studentId = UUID.randomUUID();
        when(matchingService.getMatchingOffersForStudent(eq(studentId), eq(50)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Introuvable"));

        mvc.perform(get("/v1/analytics/matchings/students/" + studentId)
                        .header("Authorization", "Bearer " + token(studentId, "ETUDIANT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    void offresRecommandees_renvoie200() throws Exception {
        UUID studentId = UUID.randomUUID();
        OfferMatchResult match = new OfferMatchResult()
                .offerId(UUID.randomUUID())
                .offerTitle("Développeur Java")
                .companyName("Atos")
                .matchScore(90);
        when(matchingService.getMatchingOffersForStudent(eq(studentId), eq(60))).thenReturn(List.of(match));

        mvc.perform(get("/v1/analytics/matchings/students/" + studentId)
                        .param("minScore", "60")
                        .header("Authorization", "Bearer " + token(studentId, "ETUDIANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offerTitle").value("Développeur Java"))
                .andExpect(jsonPath("$[0].matchScore").value(90));
    }

    private static String token(UUID userId, String role) {
        return Jwts.builder().issuer(ISSUER).subject(userId.toString())
                .claim("role", role).claim("email", "etu@uasz.sn")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
    }
}
