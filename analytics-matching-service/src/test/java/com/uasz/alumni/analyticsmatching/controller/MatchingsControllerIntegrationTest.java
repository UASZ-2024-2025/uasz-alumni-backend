package com.uasz.alumni.analyticsmatching.controller;

import com.uasz.alumni.alumnistudentcompany.spi.model.AvailabilityStatus;
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

    // =========================================================================
    // TÂCHE 2 : GET /v1/analytics/matchings/students/{studentId}
    // =========================================================================

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

    // =========================================================================
    // TÂCHE 3 : GET /v1/analytics/matchings/offers/{offerId}?minScore=60
    // =========================================================================

    @Test
    @DisplayName("GET /v1/analytics/matchings/offers/{id} - Succès par l'auteur : étudiants disponibles uniquement, score >= minScore, tri décroissant")
    void getMatchingCandidates_authorSuccess_availableOnlyAndSorted() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        String authorToken = generateToken(authorId, "recruiter@orange.sn", "ALUMNI");

        JobOffer offer = new JobOffer();
        offer.setId(offerId);
        offer.setTitle("Développeur Java / Cloud");
        offer.setPublisherId(authorId);
        offer.setRequiredSkills(List.of("Java", "Spring Boot", "Docker"));
        when(jobOfferServiceClient.getOfferById(offerId)).thenReturn(Optional.of(offer));

        // Candidat 1 : disponible, 100% (Java, Spring Boot, Docker) -> 3/3
        StudentProfile student1 = new StudentProfile();
        student1.setId(UUID.randomUUID());
        student1.setFirstName("Fatou");
        student1.setLastName("Diop");
        student1.setSpecialty("Génie Logiciel");
        student1.setAvailabilityStatus(AvailabilityStatus.AVAILABLE_FOR_JOB);
        student1.setSkills(List.of("Java", "Spring Boot", "Docker", "Kubernetes"));

        // Candidat 2 : disponible, 67% (Java, Spring Boot) -> 2/3 (>= 60%)
        StudentProfile student2 = new StudentProfile();
        student2.setId(UUID.randomUUID());
        student2.setFirstName("Moussa");
        student2.setLastName("Sow");
        student2.setSpecialty("Réseaux & Télécoms");
        student2.setAvailabilityStatus(AvailabilityStatus.AVAILABLE_IMMEDIATELY);
        student2.setSkills(List.of("Java", "Spring Boot"));

        // Candidat 3 : disponible, 33% (Java uniquement) -> 1/3 (< 60% -> exclu)
        StudentProfile student3 = new StudentProfile();
        student3.setId(UUID.randomUUID());
        student3.setFirstName("Awa");
        student3.setLastName("Ndiaye");
        student3.setSpecialty("Génie Logiciel");
        student3.setAvailabilityStatus(AvailabilityStatus.AVAILABLE_FOR_INTERNSHIP);
        student3.setSkills(List.of("Java"));

        // Candidat 4 : 100% de compétences MAIS indisponible (NOT_AVAILABLE) -> doit être exclu
        StudentProfile student4 = new StudentProfile();
        student4.setId(UUID.randomUUID());
        student4.setFirstName("Ibrahima");
        student4.setLastName("Ba");
        student4.setSpecialty("Génie Logiciel");
        student4.setAvailabilityStatus(AvailabilityStatus.NOT_AVAILABLE);
        student4.setSkills(List.of("Java", "Spring Boot", "Docker"));

        when(studentServiceClient.getAllStudents())
                .thenReturn(List.of(student1, student2, student3, student4));

        mockMvc.perform(get("/v1/analytics/matchings/offers/{offerId}", offerId)
                        .param("minScore", "60")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                // Seuls candidat 1 (100%) et candidat 2 (67%) doivent être retournés
                .andExpect(jsonPath("$", hasSize(2)))
                // Triés par score décroissant : Fatou (100%) puis Moussa (67%)
                .andExpect(jsonPath("$[0].studentName", is("Fatou Diop")))
                .andExpect(jsonPath("$[0].matchScore", is(100)))
                .andExpect(jsonPath("$[0].specialty", is("Génie Logiciel")))
                .andExpect(jsonPath("$[0].matchingSkills", hasSize(3)))
                .andExpect(jsonPath("$[1].studentName", is("Moussa Sow")))
                .andExpect(jsonPath("$[1].matchScore", is(67)))
                .andExpect(jsonPath("$[1].specialty", is("Réseaux & Télécoms")))
                .andExpect(jsonPath("$[1].matchingSkills", hasSize(2)));
    }

    @Test
    @DisplayName("GET /v1/analytics/matchings/offers/{id} - Succès par l'administrateur même s'il n'est pas l'auteur")
    void getMatchingCandidates_adminSuccess() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        String adminToken = generateToken(adminId, "admin@uasz.sn", "ADMINISTRATEUR");

        JobOffer offer = new JobOffer();
        offer.setId(offerId);
        offer.setTitle("Ingénieur DevOps");
        offer.setPublisherId(authorId);
        offer.setRequiredSkills(List.of("Docker", "Kubernetes"));
        when(jobOfferServiceClient.getOfferById(offerId)).thenReturn(Optional.of(offer));

        StudentProfile student = new StudentProfile();
        student.setId(UUID.randomUUID());
        student.setFirstName("Alioune");
        student.setLastName("Fall");
        student.setAvailabilityStatus(AvailabilityStatus.AVAILABLE_FOR_JOB);
        student.setSkills(List.of("Docker", "Kubernetes"));
        when(studentServiceClient.getAllStudents()).thenReturn(List.of(student));

        mockMvc.perform(get("/v1/analytics/matchings/offers/{offerId}", offerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentName", is("Alioune Fall")))
                .andExpect(jsonPath("$[0].matchScore", is(100)));
    }

    @Test
    @DisplayName("GET /v1/analytics/matchings/offers/{id} - Accès refusé (403) si l'utilisateur n'est ni auteur ni administrateur")
    void getMatchingCandidates_forbiddenIfNotAuthorOrAdmin() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        String otherUserToken = generateToken(otherUserId, "other@univ-zig.sn", "ETUDIANT");

        JobOffer offer = new JobOffer();
        offer.setId(offerId);
        offer.setTitle("Stage Data Science");
        offer.setPublisherId(authorId);
        when(jobOfferServiceClient.getOfferById(offerId)).thenReturn(Optional.of(offer));

        mockMvc.perform(get("/v1/analytics/matchings/offers/{offerId}", offerId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("GET /v1/analytics/matchings/offers/{id} - CRITÈRE D'ACCEPTATION : 404 si l'offre n'existe pas")
    void getMatchingCandidates_offerNotFound_returns404() throws Exception {
        UUID unknownOfferId = UUID.randomUUID();
        when(jobOfferServiceClient.getOfferById(unknownOfferId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/analytics/matchings/offers/{offerId}", unknownOfferId)
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /v1/analytics/matchings/offers/{id} - Requête non authentifiée renvoie 401 Unauthorized")
    void getMatchingCandidates_unauthenticated_returns401() throws Exception {
        UUID offerId = UUID.randomUUID();

        mockMvc.perform(get("/v1/analytics/matchings/offers/{offerId}", offerId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }
}
