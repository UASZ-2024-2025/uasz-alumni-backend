package com.uasz.alumni.alumnistudentcompany;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.model.StudentProfileEntity;
import com.uasz.alumni.alumnistudentcompany.repository.StudentProfileRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.AvailabilityStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Critères du ticket : recherche par compétence / disponibilité (paginée), consultation, mise à jour protégée. */
@SpringBootTest
@ActiveProfiles("test")
class StudentsApiIntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    StudentProfileRepository repository;

    MockMvc mvc;
    StudentProfileEntity aminata;
    StudentProfileEntity moussa;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        repository.deleteAll();

        aminata = student("Aminata", "Diallo", "Génie Logiciel", 2026,
                AvailabilityStatus.AVAILABLE_FOR_INTERNSHIP, List.of("Java", "Spring Boot", "Docker"));
        moussa = student("Moussa", "Ndiaye", "Réseaux et Télécoms", 2026,
                AvailabilityStatus.AVAILABLE_FOR_JOB, List.of("Python", "Docker"));
        student("Fatou", "Sow", "Génie Logiciel", 2025,
                AvailabilityStatus.AVAILABLE_IMMEDIATELY, List.of("React", "TypeScript"));
        student("Ibrahima", "Ba", "Data Science", 2026,
                AvailabilityStatus.NOT_AVAILABLE, List.of("Python", "SQL"));
        student("Awa", "Fall", "Génie Logiciel", 2026,
                AvailabilityStatus.AVAILABLE_FOR_INTERNSHIP, List.of("Java", "Kafka"));
    }

    // ---------- Recherche ----------

    @Test
    void searchBySkillReturnsOnlyMatchingStudents_caseInsensitive() throws Exception {
        mvc.perform(get("/v1/students").param("query", "JAVA").with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Diallo", "Fall")));
    }

    @Test
    void searchByAvailabilityReturnsOnlyMatchingStudents() throws Exception {
        mvc.perform(get("/v1/students").param("availability", "AVAILABLE_FOR_INTERNSHIP")
                        .with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Diallo", "Fall")));
    }

    @Test
    void searchBySkillAndAvailabilityCombinesBothFilters() throws Exception {
        mvc.perform(get("/v1/students").param("query", "docker").param("availability", "AVAILABLE_FOR_JOB")
                        .with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Ndiaye"));
    }

    @Test
    void searchWithSeveralWordsRequiresEveryWordToMatch() throws Exception {
        mvc.perform(get("/v1/students").param("query", "python sql").with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Ba")));
    }

    @Test
    void searchBySpecialtyAndGraduationYear() throws Exception {
        mvc.perform(get("/v1/students").param("specialty", "logiciel").param("graduationYear", "2026")
                        .with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Diallo", "Fall")));
    }

    @Test
    void searchTreatsPercentSignAsPlainText() throws Exception {
        mvc.perform(get("/v1/students").param("query", "%").with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").value(empty()));
    }

    // ---------- Pagination ----------

    @Test
    void paginationSplitsResultsWithoutOverlapAndReportsTotal() throws Exception {
        RequestPostProcessor user = asUser(UUID.randomUUID(), "ALUMNI");
        mvc.perform(get("/v1/students").param("size", "2").param("page", "0").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Ba", "Diallo")));
        mvc.perform(get("/v1/students").param("size", "2").param("page", "1").with(user))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Fall", "Ndiaye")));
        mvc.perform(get("/v1/students").param("size", "2").param("page", "2").with(user))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Sow")));
    }

    @Test
    void paginationAppliesToFilteredResults() throws Exception {
        mvc.perform(get("/v1/students").param("query", "java").param("size", "1").param("page", "1")
                        .with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Fall")));
    }

    @Test
    void invalidPaginationParametersAreRejected() throws Exception {
        RequestPostProcessor user = asUser(UUID.randomUUID(), "ALUMNI");
        mvc.perform(get("/v1/students").param("size", "0").with(user)).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/students").param("size", "101").with(user)).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/students").param("page", "-1").with(user)).andExpect(status().isBadRequest());
    }

    // ---------- Consultation ----------

    @Test
    void getByIdReturnsTheProfile() throws Exception {
        mvc.perform(get("/v1/students/{id}", aminata.getId()).with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aminata.getId().toString()))
                .andExpect(jsonPath("$.userId").value(aminata.getUserId().toString()))
                .andExpect(jsonPath("$.specialty").value("Génie Logiciel"))
                .andExpect(jsonPath("$.skills").value(containsInAnyOrder("Java", "Spring Boot", "Docker")))
                .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE_FOR_INTERNSHIP"));
    }

    @Test
    void getByIdUnknownReturns404() throws Exception {
        mvc.perform(get("/v1/students/{id}", UUID.randomUUID()).with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/v1/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/students/{id}", aminata.getId())).andExpect(status().isUnauthorized());
    }

    // ---------- Mise à jour ----------

    private static final String UPDATE_BODY = """
            {"specialty":"Génie Logiciel","academicYear":2026,"bio":"Passionnée de backend",
             "skills":["Java"," kafka ","KAFKA","Kubernetes"],"interests":["IA"],
             "availabilityStatus":"AVAILABLE_FOR_JOB","availabilityDate":"2027-01-15"}""";

    @Test
    void ownerCanUpdateOwnProfile_andSearchSeesTheChange() throws Exception {
        mvc.perform(put("/v1/students/{id}", aminata.getId()).with(asUser(aminata.getUserId(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Passionnée de backend"))
                .andExpect(jsonPath("$.skills").value(contains("Java", "kafka", "Kubernetes")))
                .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE_FOR_JOB"))
                .andExpect(jsonPath("$.availabilityDate").value("2027-01-15"));

        mvc.perform(get("/v1/students").param("query", "kubernetes").param("availability", "AVAILABLE_FOR_JOB")
                        .with(asUser(UUID.randomUUID(), "ALUMNI")))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Diallo")));
    }

    @Test
    void anotherStudentCannotUpdateTheProfile() throws Exception {
        mvc.perform(put("/v1/students/{id}", aminata.getId()).with(asUser(moussa.getUserId(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void administratorCanUpdateAnyProfile() throws Exception {
        mvc.perform(put("/v1/students/{id}", aminata.getId()).with(asUser(UUID.randomUUID(), "ADMINISTRATEUR"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void updateUnknownProfileReturns404() throws Exception {
        mvc.perform(put("/v1/students/{id}", UUID.randomUUID()).with(asUser(UUID.randomUUID(), "ADMINISTRATEUR"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWithInvalidDataReturns400() throws Exception {
        String tooLongSkill = "x".repeat(51);
        mvc.perform(put("/v1/students/{id}", aminata.getId()).with(asUser(aminata.getUserId(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[\"" + tooLongSkill + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateWithoutTokenReturns401() throws Exception {
        mvc.perform(put("/v1/students/{id}", aminata.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Utilitaires ----------

    private StudentProfileEntity student(String first, String last, String specialty, int year,
                                         AvailabilityStatus status, List<String> skills) {
        StudentProfileEntity e = new StudentProfileEntity(UUID.randomUUID(), first, last,
                first.toLowerCase() + "." + last.toLowerCase() + "@zig.univ.sn");
        e.setSpecialty(specialty);
        e.setAcademicYear(year);
        e.setAvailabilityStatus(status);
        e.setSkills(skills);
        return repository.save(e);
    }

    private static RequestPostProcessor asUser(UUID userId, String role) {
        return jwt().jwt(j -> j.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
