package com.uasz.alumni.analyticsmatching.controller;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import com.uasz.alumni.analyticsmatching.client.JobOfferServiceClient;
import com.uasz.alumni.analyticsmatching.client.StudentServiceClient;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class MatchingsControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private StudentServiceClient studentServiceClient;

    @MockitoBean
    private JobOfferServiceClient jobOfferServiceClient;

    private String validToken;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        studentId = UUID.randomUUID();
        validToken = generateToken(studentId, "student@univ-zig.sn", "ETUDIANT");
    }

    private String generateToken(UUID userId, String email, String role) {
        byte[] keyBytes = Decoders.BASE64.decode("dGVzdC1zZWNyZXQta2V5LWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmctMTIzNDU2Nzg5MA==");
        return Jwts.builder()
                .subject(userId.toString())
                .issuer("alumni-uasz-user")
                .claim("email", email)
                .claim("role", role)
                .claim("status", "ACTIF")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();
    }

    @Test
    @DisplayName("GET /v1/analytics/matchings/students/{id} - Succès avec calcul de score et tri décroissant")
    void getMatchingOffers_success_sortedByScoreDescending() throws Exception {
        // Étudiant possédant Java, Spring Boot, React, Docker
        StudentProfile student = new StudentProfile();
        student.setId(studentId);
        student.setFirstName("Mamadou");
        student.setLastName("Diallo");
        student.setSkills(List.of("Java", "Spring Boot", "React", "Docker"));
        when(studentServiceClient.getStudentProfile(studentId)).thenReturn(Optional.of(student));

        // Offre 1 : 100% match (Java, Spring Boot, Docker) -> 3/3 = 100%
        JobOffer offer1 = new JobOffer();
        offer1.setId(UUID.randomUUID());
        offer1.setTitle("Ingénieur Backend Senior");
        offer1.setCompanyName("Orange Sénégal");
        offer1.setStatus(OfferStatus.OPEN);
        offer1.setRequiredSkills(List.of("Java", "Spring Boot", "Docker"));

        // Offre 2 : 50% match (Java, Python) -> 1/2 = 50% (égal au minScore par défaut 50%)
        JobOffer offer2 = new JobOffer();
        offer2.setId(UUID.randomUUID());
        offer2.setTitle("Développeur Polyvalent");
        offer2.setCompanyName("Sonatel");
        offer2.setStatus(OfferStatus.OPEN);
        offer2.setRequiredSkills(List.of("Java", "Python"));

        // Offre 3 : 25% match (React, Vue.js, Angular, Node.js) -> 1/4 = 25% (< 50% -> doit être exclue)
        JobOffer offer3 = new JobOffer();
        offer3.setId(UUID.randomUUID());
        offer3.setTitle("Lead Frontend");
        offer3.setCompanyName("Gainde 2000");
        offer3.setStatus(OfferStatus.OPEN);
        offer3.setRequiredSkills(List.of("React", "Vue.js", "Angular", "Node.js"));

        // Offre 4 : 100% de compétences mais statut CLOSED -> doit être exclue
        JobOffer offer4 = new JobOffer();
        offer4.setId(UUID.randomUUID());
        offer4.setTitle("Stage Java Clôturé");
        offer4.setCompanyName("Bolloré");
        offer4.setStatus(OfferStatus.CLOSED);
        offer4.setRequiredSkills(List.of("Java", "Docker"));

        when(jobOfferServiceClient.getJobOffers(OfferStatus.OPEN))
                .thenReturn(List.of(offer1, offer2, offer3, offer4));

        // Exécution de l'appel GET avec minScore=50
        mockMvc.perform(get("/v1/analytics/matchings/students/{studentId}", studentId)
                        .param("minScore", "50")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                // Seules les offres 1 (100%) et 2 (50%) doivent être retournées
                .andExpect(jsonPath("$", hasSize(2)))
                // Triées par score décroissant : le 100% en premier, le 50% en second
                .andExpect(jsonPath("$[0].offerTitle", is("Ingénieur Backend Senior")))
                .andExpect(jsonPath("$[0].matchScore", is(100)))
                .andExpect(jsonPath("$[0].matchingSkills", hasSize(3)))
                .andExpect(jsonPath("$[0].missingSkills", hasSize(0)))
                .andExpect(jsonPath("$[1].offerTitle", is("Développeur Polyvalent")))
                .andExpect(jsonPath("$[1].matchScore", is(50)))
                .andExpect(jsonPath("$[1].matchingSkills", hasSize(1)))
                .andExpect(jsonPath("$[1].missingSkills", hasSize(1)));
    }

    @Test
    @DisplayName("GET /v1/analytics/matchings/students/{id} - CRITÈRE D'ACCEPTATION : 404 si l'étudiant n'existe pas")
    void getMatchingOffers_studentNotFound_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(studentServiceClient.getStudentProfile(unknownId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/analytics/matchings/students/{studentId}", unknownId)
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /v1/analytics/matchings/students/{id} - CRITÈRE D'ACCEPTATION : seules les offres au-dessus de minScore sont renvoyées")
    void getMatchingOffers_filterByCustomMinScore() throws Exception {
        StudentProfile student = new StudentProfile();
        student.setId(studentId);
        student.setSkills(List.of("Java", "Spring Boot"));
        when(studentServiceClient.getStudentProfile(studentId)).thenReturn(Optional.of(student));

        // Offre à 67% (2/3 : Java, Spring Boot, AWS)
        JobOffer offer = new JobOffer();
        offer.setId(UUID.randomUUID());
        offer.setTitle("Dev Cloud Java");
        offer.setStatus(OfferStatus.OPEN);
        offer.setRequiredSkills(List.of("Java", "Spring Boot", "AWS"));

        when(jobOfferServiceClient.getJobOffers(OfferStatus.OPEN)).thenReturn(List.of(offer));

        // Si minScore = 70 : l'offre à 67% doit être filtrée -> liste vide
        mockMvc.perform(get("/v1/analytics/matchings/students/{studentId}", studentId)
                        .param("minScore", "70")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Si minScore = 60 : l'offre à 67% doit être renvoyée
        mockMvc.perform(get("/v1/analytics/matchings/students/{studentId}", studentId)
                        .param("minScore", "60")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].matchScore", is(67)));
    }

    @Test
    @DisplayName("GET /v1/analytics/matchings/students/{id} - Requête sans authentification renvoie 401 Unauthorized")
    void getMatchingOffers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/v1/analytics/matchings/students/{studentId}", studentId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }
}
