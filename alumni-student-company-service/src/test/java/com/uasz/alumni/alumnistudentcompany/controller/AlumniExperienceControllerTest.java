package com.uasz.alumni.alumnistudentcompany.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.config.ApiPrefixConfig;
import com.uasz.alumni.alumnistudentcompany.config.SecurityConfig;
import com.uasz.alumni.alumnistudentcompany.model.Experience;
import com.uasz.alumni.alumnistudentcompany.service.AlumniExperienceService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AlumniController.class)
@Import({SecurityConfig.class, ApiPrefixConfig.class})
@TestPropertySource(properties = "security.api.prefix=/v1")
class AlumniExperienceControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AlumniExperienceService service;
    @MockitoBean JwtDecoder jwtDecoder;

    private final UUID user = UUID.randomUUID();
    private final UUID alumniId = UUID.randomUUID();
    private final String url = "/v1/alumni/" + alumniId + "/experiences";

    private RequestPostProcessor token(String role) {
        return jwt().jwt(j -> j.subject(user.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static final String VALID_BODY = """
            {"companyName":"Atos","position":"Architecte Logiciel","startDate":"2024-01-01","isCurrent":true}
            """;

    @Test
    void alumniAjouteUneExperienceEt201() throws Exception {
        when(service.addExperience(eq(alumniId), any(), eq(user))).thenReturn(
                new Experience(alumniId, "Atos", "Architecte Logiciel", LocalDate.of(2024, 1, 1), null, true, null));

        mvc.perform(post(url).with(token("ALUMNI")).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Atos"))
                .andExpect(jsonPath("$.isCurrent").value(true));
    }

    @Test
    void champObligatoireManquantDonne400() throws Exception {
        mvc.perform(post(url).with(token("ALUMNI")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Atos\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).addExperience(any(), any(), any());
    }

    @Test
    void etudiantNePeutPasAjouterDExperience() throws Exception {
        mvc.perform(post(url).with(token("ETUDIANT")).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void sansJetonDonne401() throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void listeDuParcours() throws Exception {
        when(service.listExperiences(alumniId)).thenReturn(List.of(
                new Experience(alumniId, "Atos", "Architecte", LocalDate.of(2024, 1, 1), null, true, null),
                new Experience(alumniId, "Sonatel", "Développeur", LocalDate.of(2021, 3, 1),
                        LocalDate.of(2023, 12, 31), false, null)));

        mvc.perform(get(url).with(token("ETUDIANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].isCurrent").value(true))
                .andExpect(jsonPath("$[1].endDate").value("2023-12-31"));
    }
}
