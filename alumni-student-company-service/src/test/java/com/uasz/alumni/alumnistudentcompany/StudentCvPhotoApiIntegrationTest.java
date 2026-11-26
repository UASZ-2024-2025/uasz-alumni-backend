package com.uasz.alumni.alumnistudentcompany;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.model.StudentProfileEntity;
import com.uasz.alumni.alumnistudentcompany.repository.StudentCvPhotoRepository;
import com.uasz.alumni.alumnistudentcompany.repository.StudentCvRepository;
import com.uasz.alumni.alumnistudentcompany.repository.StudentProfileRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * ALUM-255 : photo du CV. JPEG ou PNG de 2 Mo au plus, rattachée au profil de l'étudiant, remplaçable et
 * supprimable ; seuls l'étudiant et l'administrateur peuvent la lire ou la modifier.
 */
@SpringBootTest
@ActiveProfiles("test")
class StudentCvPhotoApiIntegrationTest {

    /** Débuts de fichiers réels : la signature suffit au contrôle de format. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final String PHOTO = "/v1/students/{id}/cv/photo";
    private static final String CV = """
            {"title":"Développeuse Java","template":"MODERN_BLUE","language":"Français","skills":["Java"]}
            """;

    @Autowired
    WebApplicationContext context;
    @Autowired
    StudentProfileRepository students;
    @Autowired
    StudentCvRepository cvs;
    @Autowired
    StudentCvPhotoRepository photos;
    @Value("${jwt.secret}")
    String secret;
    @Value("${jwt.issuer}")
    String issuer;
    MockMvc mvc;
    StudentProfileEntity aminata;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        photos.deleteAll();
        cvs.deleteAll();
        students.deleteAll();
        aminata = students.save(new StudentProfileEntity(UUID.randomUUID(), "Aminata", "Diallo", "a.d@zig.univ.sn"));
    }

    @Test
    void studentUploadsHerPhoto_byAccountId_andReadsItBack() throws Exception {
        upload(aminata.getUserId(), "photo.png", MediaType.IMAGE_PNG_VALUE, PNG, as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isNoContent());

        mvc.perform(get(PHOTO, aminata.getId()).with(as(aminata.getUserId(), "ETUDIANT")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void aNewPhotoReplacesThePreviousOne() throws Exception {
        upload(aminata.getId(), "a.png", MediaType.IMAGE_PNG_VALUE, PNG, as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isNoContent());
        upload(aminata.getId(), "b.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG, as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isNoContent());

        mvc.perform(get(PHOTO, aminata.getId()).with(as(aminata.getUserId(), "ETUDIANT")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void formatIsCheckedOnTheContent_notOnTheNameOrDeclaredType() throws Exception {
        byte[] pdf = "%PDF-1.7 faux".getBytes();
        upload(aminata.getId(), "photo.png", MediaType.IMAGE_PNG_VALUE, pdf, as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO_FORMAT"));
        upload(aminata.getId(), "vide.png", MediaType.IMAGE_PNG_VALUE, new byte[0], as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO_FORMAT"));
    }

    @Test
    void photoOver2MbIsRejected_andExactly2MbIsAccepted() throws Exception {
        byte[] tooBig = Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1);
        upload(aminata.getId(), "grande.png", MediaType.IMAGE_PNG_VALUE, tooBig, as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));

        byte[] limit = Arrays.copyOf(PNG, 2 * 1024 * 1024);
        upload(aminata.getId(), "limite.png", MediaType.IMAGE_PNG_VALUE, limit, as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyTheStudentAndAnAdministratorCanAccessThePhoto() throws Exception {
        upload(aminata.getId(), "photo.png", MediaType.IMAGE_PNG_VALUE, PNG, as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isNoContent());

        for (String role : new String[] {"ETUDIANT", "ALUMNI", "ENTREPRISE", "ENSEIGNANT"}) {
            mvc.perform(get(PHOTO, aminata.getId()).with(as(UUID.randomUUID(), role)))
                    .andExpect(status().isForbidden());
        }
        upload(aminata.getId(), "x.png", MediaType.IMAGE_PNG_VALUE, PNG, as(UUID.randomUUID(), "ETUDIANT"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PHOTO, aminata.getId()).with(as(UUID.randomUUID(), "ALUMNI")))
                .andExpect(status().isForbidden());

        mvc.perform(get(PHOTO, aminata.getId()).with(as(UUID.randomUUID(), "ADMINISTRATEUR")))
                .andExpect(status().isOk());
    }

    @Test
    void deletingRemovesThePhoto_andIsHarmlessWhenThereIsNone() throws Exception {
        upload(aminata.getId(), "photo.png", MediaType.IMAGE_PNG_VALUE, PNG, as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isNoContent());

        mvc.perform(delete(PHOTO, aminata.getId()).with(as(aminata.getUserId(), "ETUDIANT")))
                .andExpect(status().isNoContent());
        mvc.perform(get(PHOTO, aminata.getId()).with(as(aminata.getUserId(), "ETUDIANT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
        mvc.perform(delete(PHOTO, aminata.getId()).with(as(aminata.getUserId(), "ETUDIANT")))
                .andExpect(status().isNoContent());
    }

    @Test
    void hasPhotoInTheCv_isTrueOnlyForThoseWhoCanReadThePhoto() throws Exception {
        mvc.perform(put("/v1/students/{id}/cv", aminata.getId()).with(as(aminata.getUserId(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON).content(CV))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(false));

        upload(aminata.getId(), "photo.png", MediaType.IMAGE_PNG_VALUE, PNG, as(aminata.getUserId(), "ETUDIANT"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/v1/students/{id}/cv", aminata.getId()).with(as(aminata.getUserId(), "ETUDIANT")))
                .andExpect(jsonPath("$.hasPhoto").value(true));
        mvc.perform(get("/v1/students/{id}/cv", aminata.getId()).with(as(UUID.randomUUID(), "ADMINISTRATEUR")))
                .andExpect(jsonPath("$.hasPhoto").value(true));
        // Un recruteur lit le CV, mais pas la photo : il ne doit pas essayer de la charger.
        mvc.perform(get("/v1/students/{id}/cv", aminata.getId()).with(as(UUID.randomUUID(), "ENTREPRISE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(false));
    }

    @Test
    void unknownStudentReturns404_andNoTokenReturns401() throws Exception {
        upload(UUID.randomUUID(), "photo.png", MediaType.IMAGE_PNG_VALUE, PNG, as(UUID.randomUUID(), "ADMINISTRATEUR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        mvc.perform(get(PHOTO, aminata.getId()))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions upload(UUID id, String name, String type, byte[] data, RequestPostProcessor who)
            throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, PHOTO, id)
                .file(new MockMultipartFile("file", name, type, data))
                .with(who));
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
