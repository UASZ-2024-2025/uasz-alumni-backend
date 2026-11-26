package com.uasz.alumni.alumnistudentcompany.controller;
import com.uasz.alumni.alumnistudentcompany.service.RecommendationService;
import com.uasz.alumni.alumnistudentcompany.service.AlumniProfileService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.config.ApiPrefixConfig;
import com.uasz.alumni.alumnistudentcompany.config.SecurityConfig;
import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.alumnistudentcompany.jwt.JwtService;
import com.uasz.alumni.alumnistudentcompany.model.Experience;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.service.AlumniExperienceService;
import com.uasz.alumni.alumnistudentcompany.service.AlumniHomeSummaryService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Routes /alumni/{id}/experiences côté web : filtre JWT réel, seul le parsing du jeton est simulé. */
@WebMvcTest(controllers = AlumniController.class, properties = "security.api.prefix=/v1")
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class AlumniExperienceControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AlumniExperienceService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AlumniProfileService profileService;
    @MockitoBean RecommendationService recommendationService;
    @MockitoBean AlumniHomeSummaryService homeSummaryService;

    private final UUID user = UUID.randomUUID();
    private final UUID alumniId = UUID.randomUUID();
    private final String url = "/v1/alumni/" + alumniId + "/experiences";

    private static final String VALID_BODY = """
            {"companyName":"Atos","position":"Architecte Logiciel","startDate":"2024-01-01","isCurrent":true}
            """;

    @BeforeEach
    void tokens() {
        when(jwtService.parse(anyString())).thenReturn(Optional.empty());
        when(jwtService.parse("alumni")).thenReturn(Optional.of(new AuthenticatedUser(user, "a@uasz.sn", "ALUMNI")));
        when(jwtService.parse("etudiant")).thenReturn(Optional.of(new AuthenticatedUser(user, "e@uasz.sn", "ETUDIANT")));
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder postBody(String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void alumniAjouteUneExperienceEt201() throws Exception {
        when(service.addExperience(eq(alumniId), any(), eq(user))).thenReturn(
                new Experience(alumniId, "Atos", "Architecte Logiciel", LocalDate.of(2024, 1, 1), null, true, null));

        mvc.perform(as("alumni", postBody(VALID_BODY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Atos"))
                .andExpect(jsonPath("$.isCurrent").value(true));
    }

    @Test
    void datesIncoherentesDonnent400AvecUnMessage() throws Exception {
        when(service.addExperience(eq(alumniId), any(), eq(user))).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "INVALID_EXPERIENCE",
                "La date de fin doit être postérieure ou égale à la date de début"));

        mvc.perform(as("alumni", postBody("""
                        {"companyName":"Atos","position":"Dev","startDate":"2024-01-01","endDate":"2023-01-01"}
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPERIENCE"))
                .andExpect(jsonPath("$.message").value("La date de fin doit être postérieure ou égale à la date de début"));
    }

    @Test
    void champObligatoireManquantDonne400() throws Exception {
        mvc.perform(as("alumni", postBody("{\"companyName\":\"Atos\"}")))
                .andExpect(status().isBadRequest());
        verify(service, never()).addExperience(any(), any(), any());
    }

    @Test
    void etudiantNePeutPasAjouterDExperience() throws Exception {
        mvc.perform(as("etudiant", postBody(VALID_BODY))).andExpect(status().isForbidden());
        verify(service, never()).addExperience(any(), any(), any());
    }

    @Test
    void sansJetonOuJetonInvalideDonne401() throws Exception {
        mvc.perform(postBody(VALID_BODY)).andExpect(status().isUnauthorized());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(as("faux", get(url))).andExpect(status().isUnauthorized());
    }

    @Test
    void listeDuParcours() throws Exception {
        when(service.listExperiences(alumniId)).thenReturn(List.of(
                new Experience(alumniId, "Atos", "Architecte", LocalDate.of(2024, 1, 1), null, true, null),
                new Experience(alumniId, "Sonatel", "Développeur", LocalDate.of(2021, 3, 1),
                        LocalDate.of(2023, 12, 31), false, null)));

        mvc.perform(as("etudiant", get(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].isCurrent").value(true))
                .andExpect(jsonPath("$[1].endDate").value("2023-12-31"));
    }
}
