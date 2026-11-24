package com.uasz.alumni.alumnistudentcompany.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.config.ApiPrefixConfig;
import com.uasz.alumni.alumnistudentcompany.config.SecurityConfig;
import com.uasz.alumni.alumnistudentcompany.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.alumnistudentcompany.jwt.JwtService;
import com.uasz.alumni.alumnistudentcompany.model.Recommendation;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.service.AlumniExperienceService;
import com.uasz.alumni.alumnistudentcompany.service.AlumniProfileService;
import com.uasz.alumni.alumnistudentcompany.service.RecommendationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** POST /alumni/recommendations : filtre JWT réel, seul le parsing du jeton est simulé. */
@WebMvcTest(controllers = AlumniController.class, properties = "security.api.prefix=/v1")
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class AlumniControllerTest {

    private static final String URL = "/v1/alumni/recommendations";

    @Autowired MockMvc mvc;
    @MockitoBean RecommendationService service;
    @MockitoBean AlumniExperienceService experiences;
    @MockitoBean AlumniProfileService profiles;
    @MockitoBean JwtService jwtService;

    private final UUID alumni = UUID.randomUUID();
    private final UUID etudiant = UUID.randomUUID();
    private final UUID entreprise = UUID.randomUUID();

    @BeforeEach
    void tokens() {
        when(jwtService.parse(anyString())).thenReturn(Optional.empty());
        when(jwtService.parse("alumni")).thenReturn(Optional.of(new AuthenticatedUser(alumni, "a@uasz.sn", "ALUMNI")));
        when(jwtService.parse("etudiant")).thenReturn(Optional.of(new AuthenticatedUser(alumni, "e@uasz.sn", "ETUDIANT")));
    }

    private String corps() {
        return "{\"studentId\":\"%s\",\"companyId\":\"%s\",\"comments\":\"Excellent\"}".formatted(etudiant, entreprise);
    }

    private MockHttpServletRequestBuilder postAs(String token, String body) {
        return post(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void alumniObtient201() throws Exception {
        when(service.recommander(eq(alumni), eq(etudiant), eq(entreprise), eq("Excellent")))
            .thenReturn(new Recommendation(alumni, etudiant, entreprise, "Excellent"));

        mvc.perform(postAs("alumni", corps()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recommenderId").value(alumni.toString()));
    }

    @Test
    void nonAlumniObtient403() throws Exception {
        mvc.perform(postAs("etudiant", corps())).andExpect(status().isForbidden());
    }

    @Test
    void sansJetonObtient401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corps()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void champManquantObtient400() throws Exception {
        mvc.perform(postAs("alumni", "{\"studentId\":\"%s\"}".formatted(etudiant)))
            .andExpect(status().isBadRequest());
    }
}
