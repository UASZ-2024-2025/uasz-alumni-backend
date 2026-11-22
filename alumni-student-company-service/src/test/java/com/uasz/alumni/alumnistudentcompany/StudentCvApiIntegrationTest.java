package com.uasz.alumni.alumnistudentcompany;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.model.StudentProfileEntity;
import com.uasz.alumni.alumnistudentcompany.repository.StudentCvRepository;
import com.uasz.alumni.alumnistudentcompany.repository.StudentProfileRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** ALUM-109 : le CV saisi est enregistré, une mise à jour remplace la version précédente. */
@SpringBootTest
@ActiveProfiles("test")
class StudentCvApiIntegrationTest {

    private static final String CV = """
            {"title":"Développeuse Java","template":"MODERN_BLUE","language":"Français",
             "summary":"  Passionnée de back-end  ","skills":["Java"," Spring ","java",""],
             "experiences":[{"companyName":"Sonatel","position":"Stagiaire","startDate":"2025-06-01",
                             "endDate":"2025-09-30","description":"API REST"},
                            {"companyName":" ","position":""}],
             "educations":[{"institution":"UASZ","degree":"Master","fieldOfStudy":"Génie logiciel",
                            "startDate":"2024-10-01"}],
             "languages":[{"language":"Anglais","proficiency":"Avancé"}]}
            """;

    @Autowired
    WebApplicationContext context;
    @Autowired
    StudentProfileRepository students;
    @Autowired
    StudentCvRepository cvs;
    @Value("${jwt.secret}")
    String secret;
    @Value("${jwt.issuer}")
    String issuer;
    MockMvc mvc;
    StudentProfileEntity aminata;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cvs.deleteAll();
        students.deleteAll();
        aminata = students.save(new StudentProfileEntity(UUID.randomUUID(), "Aminata", "Diallo", "a.d@zig.univ.sn"));
    }

    @Test
    void noCvYetReturns404() throws Exception {
        mvc.perform(get("/v1/students/{id}/cv", aminata.getId()).with(as(aminata.getUserId(), "ETUDIANT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CV_NOT_FOUND"));
    }

    @Test
    void studentSavesHisCv_byAccountId_andEveryConnectedUserCanReadIt() throws Exception {
        // le front n'a que l'id du compte : il sert d'identifiant comme l'id du profil
        String id = mvc.perform(put("/v1/students/{id}/cv", aminata.getUserId())
                        .with(as(aminata.getUserId(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON).content(CV))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(aminata.getId().toString()))
                .andExpect(jsonPath("$.summary").value("Passionnée de back-end"))
                .andExpect(jsonPath("$.skills").value(contains("Java", "Spring")))
                .andExpect(jsonPath("$.experiences.length()").value(1))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/v1/students/{id}/cv", aminata.getId()).with(as(UUID.randomUUID(), "ENTREPRISE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Développeuse Java"))
                .andExpect(jsonPath("$.experiences[0].position").value("Stagiaire"))
                .andExpect(jsonPath("$.experiences[0].endDate").value("2025-09-30"))
                .andExpect(jsonPath("$.educations[0].fieldOfStudy").value("Génie logiciel"))
                .andExpect(jsonPath("$.languages[0].proficiency").value("Avancé"));
    }

    @Test
    void updateReplacesThePreviousVersion_andKeepsTheSameCvId() throws Exception {
        String first = mvc.perform(put("/v1/students/{id}/cv", aminata.getId())
                        .with(as(aminata.getUserId(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON).content(CV))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cvId = first.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(put("/v1/students/{id}/cv", aminata.getId())
                        .with(as(aminata.getUserId(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Data analyst","template":"CLASSIC","language":"Anglais","skills":["SQL"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cvId))
                .andExpect(jsonPath("$.title").value("Data analyst"))
                .andExpect(jsonPath("$.skills").value(contains("SQL")))
                .andExpect(jsonPath("$.experiences").value(empty()))
                .andExpect(jsonPath("$.languages").value(empty()));

        mvc.perform(get("/v1/students/{id}/cv", aminata.getId()).with(as(aminata.getUserId(), "ETUDIANT")))
                .andExpect(jsonPath("$.title").value("Data analyst"))
                .andExpect(jsonPath("$.educations").value(empty()));
    }

    @Test
    void anotherStudentCannotWriteTheCv_butAnAdministratorCan() throws Exception {
        mvc.perform(put("/v1/students/{id}/cv", aminata.getId())
                        .with(as(UUID.randomUUID(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON).content(CV))
                .andExpect(status().isForbidden());
        mvc.perform(put("/v1/students/{id}/cv", aminata.getId())
                        .with(as(UUID.randomUUID(), "ADMINISTRATEUR"))
                        .contentType(MediaType.APPLICATION_JSON).content(CV))
                .andExpect(status().isOk());
    }

    @Test
    void invalidCvIsRejectedWith400() throws Exception {
        mvc.perform(put("/v1/students/{id}/cv", aminata.getId())
                        .with(as(aminata.getUserId(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","template":"MODERN_BLUE","language":"Français"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/v1/students/{id}/cv", aminata.getId())
                        .with(as(aminata.getUserId(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"CV","template":"MODERN_BLUE","language":"Français",
                                 "experiences":[{"companyName":"X","startDate":"2025-06-01","endDate":"2025-01-01"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownStudentReturns404_andNoTokenReturns401() throws Exception {
        mvc.perform(put("/v1/students/{id}/cv", UUID.randomUUID())
                        .with(as(UUID.randomUUID(), "ADMINISTRATEUR"))
                        .contentType(MediaType.APPLICATION_JSON).content(CV))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/students/{id}/cv", aminata.getId()))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor as(UUID userId, String role) {
        String token = Jwts.builder().issuer(issuer).subject(userId.toString())
                .claim("role", role).claim("email", "test@uasz.sn")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))).compact();
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }
}
