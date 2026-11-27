package com.uasz.alumni.analyticsmatching.controller;

import static org.mockito.ArgumentMatchers.any;
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
import com.uasz.alumni.analyticsmatching.service.CandidateMatchingService;
import com.uasz.alumni.analyticsmatching.service.OfferMatchingService;
import com.uasz.alumni.analyticsmatching.spi.model.CandidateMatchResult;
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
    private static final UUID STUDENT = UUID.randomUUID();
    private static final String URL = "/v1/analytics/matchings/students/" + STUDENT;

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService(SECRET, ISSUER);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean OfferMatchingService offerMatching;
    @MockitoBean CandidateMatchingService candidateMatching;

    @Test
    void sansJeton_renvoie401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void offresRecommandees_renvoie200() throws Exception {
        when(offerMatching.matchingOffers(eq(STUDENT), eq(70), any())).thenReturn(List.of(new OfferMatchResult()
                .offerId(UUID.randomUUID()).offerTitle("Développeur Java").companyName("Gainde 2000").matchScore(90)
                .matchingSkills(List.of("Java")).missingSkills(List.of())));

        mvc.perform(get(URL).param("minScore", "70").header("Authorization", "Bearer " + token(STUDENT, "ETUDIANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offerTitle").value("Développeur Java"))
                .andExpect(jsonPath("$[0].matchScore").value(90))
                .andExpect(jsonPath("$[0].matchingSkills[0]").value("Java"));
    }

    @Test
    void refusDuService_renvoie403() throws Exception {
        when(offerMatching.matchingOffers(eq(STUDENT), any(), any()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "x"));

        mvc.perform(get(URL).header("Authorization", "Bearer " + token(UUID.randomUUID(), "ETUDIANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void etudiantInconnu_renvoie404() throws Exception {
        when(offerMatching.matchingOffers(eq(STUDENT), any(), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "x"));

        mvc.perform(get(URL).header("Authorization", "Bearer " + token(STUDENT, "ADMINISTRATEUR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void candidatsRecommandes_renvoie200() throws Exception {
        UUID offer = UUID.randomUUID();
        when(candidateMatching.matchingCandidates(eq(offer), eq(60), any())).thenReturn(List.of(new CandidateMatchResult()
                .studentId(STUDENT).studentName("Awa Ndiaye").specialty("Génie Logiciel").matchScore(90)
                .matchingSkills(List.of("Java"))));

        mvc.perform(get("/v1/analytics/matchings/offers/" + offer)
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), "ENTREPRISE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("Awa Ndiaye"))
                .andExpect(jsonPath("$[0].matchScore").value(90));
    }

    @Test
    void candidatsSansJeton_renvoie401() throws Exception {
        mvc.perform(get("/v1/analytics/matchings/offers/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private static String token(UUID subject, String role) {
        return Jwts.builder().issuer(ISSUER).subject(subject.toString())
                .claim("role", role).claim("email", "user@uasz.sn")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
    }
}
