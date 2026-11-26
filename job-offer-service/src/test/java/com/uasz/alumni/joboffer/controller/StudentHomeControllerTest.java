
package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.config.ApiPrefixConfig;
import com.uasz.alumni.joboffer.config.SecurityConfig;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.service.StudentHomeSummaryService;
import com.uasz.alumni.joboffer.spi.model.StudentHomeSummary;
import com.uasz.alumni.joboffer.jwt.JwtService;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentHomeController.class)
@Import({SecurityConfig.class, ApiPrefixConfig.class})
@TestPropertySource(properties = "security.api.prefix=/v1")
class StudentHomeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private StudentHomeSummaryService service;

    /**
     * Vérifie qu'un étudiant authentifié peut consulter sa synthèse.
     */
    @Test
    void etudiantAuthentifiePeutConsulterSaSynthese() throws Exception {
        UUID studentId = UUID.randomUUID();

        when(service.summary(studentId))
            .thenReturn(new StudentHomeSummary());

        mvc.perform(get("/v1/students/me/home-summary")
                .with(token(studentId, "ETUDIANT")))
            .andExpect(status().isOk());

        verify(service).summary(studentId);
    }

    /**
     * Vérifie qu'un utilisateur non authentifié ne peut pas accéder à l'endpoint.
     */
    @Test
    void utilisateurNonAuthentifieEstRefuse() throws Exception {
        mvc.perform(get("/v1/students/me/home-summary"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Vérifie qu'un utilisateur qui n'a pas le rôle ETUDIANT
     * ne peut pas consulter la synthèse étudiant.
     */
    @Test
    void utilisateurAvecUnAutreRoleEstRefuse() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(get("/v1/students/me/home-summary")
                .with(token(userId, "ALUMNI")))
            .andExpect(status().isForbidden());
    }

    /**
     * Simule un JWT valide contenant l'identifiant et le rôle de l'utilisateur.
     */
    private RequestPostProcessor token(UUID userId, String role) {
        when(jwtService.parse("token"))
            .thenReturn(Optional.of(
                new AuthenticatedUser(userId, "u@uasz.sn", role)
            ));

        return request -> {
            request.addHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer token"
            );
            return request;
        };
    }
}

