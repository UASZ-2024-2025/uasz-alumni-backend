package com.uasz.alumni.alumnistudentcompany.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.config.ApiPrefixConfig;
import com.uasz.alumni.alumnistudentcompany.config.SecurityConfig;
import com.uasz.alumni.alumnistudentcompany.model.Recommendation;
import com.uasz.alumni.alumnistudentcompany.service.RecommendationService;
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

@WebMvcTest(AlumniController.class)
@Import({SecurityConfig.class, ApiPrefixConfig.class})
@TestPropertySource(properties = "security.api.prefix=/v1")
class AlumniControllerTest {

    private static final String URL = "/v1/alumni/recommendations";

    @Autowired MockMvc mvc;
    @MockitoBean RecommendationService service;
    @MockitoBean JwtDecoder jwtDecoder;

    private final UUID alumni = UUID.randomUUID();
    private final UUID etudiant = UUID.randomUUID();
    private final UUID entreprise = UUID.randomUUID();

    private String corps() {
        return "{\"studentId\":\"%s\",\"companyId\":\"%s\",\"comments\":\"Excellent\"}".formatted(etudiant, entreprise);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jeton(String role) {
        return jwt().jwt(j -> j.subject(alumni.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void alumniObtient201() throws Exception {
        when(service.recommander(any(), any(), any(), any(), any()))
            .thenReturn(new Recommendation(alumni, etudiant, entreprise, "Excellent"));

        mvc.perform(post(URL).with(jeton("ALUMNI")).contentType(MediaType.APPLICATION_JSON).content(corps()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recommenderId").value(alumni.toString()));
    }

    @Test
    void nonAlumniObtient403() throws Exception {
        mvc.perform(post(URL).with(jeton("ETUDIANT")).contentType(MediaType.APPLICATION_JSON).content(corps()))
            .andExpect(status().isForbidden());
    }

    @Test
    void sansJetonObtient401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corps()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void champManquantObtient400() throws Exception {
        mvc.perform(post(URL).with(jeton("ALUMNI")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"%s\"}".formatted(etudiant)))
            .andExpect(status().isBadRequest());
    }
}
