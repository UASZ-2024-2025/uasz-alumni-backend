package com.uasz.alumni.alumnistudentcompany.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.config.ApiPrefixConfig;
import com.uasz.alumni.alumnistudentcompany.config.SecurityConfig;
import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.alumnistudentcompany.jwt.JwtService;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.service.AlumniExperienceService;
import com.uasz.alumni.alumnistudentcompany.service.RecommendationService;
import com.uasz.alumni.alumnistudentcompany.service.AlumniProfileService;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniProfile;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniProfileUpdateRequest;
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
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniPagedResponse;
import java.util.List;

/** Routes /alumni/{id} côté web : filtre JWT réel, parsing du jeton simulé. */
@WebMvcTest(controllers = AlumniController.class, properties = "security.api.prefix=/v1")
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class AlumniProfileControllerTest {

    @Autowired MockMvc mvc;

    // On mocke les deux services car ils sont injectés dans le contrôleur
    @MockitoBean AlumniProfileService profileService;
    @MockitoBean RecommendationService recommendationService;
    @MockitoBean AlumniExperienceService experienceService;
    @MockitoBean JwtService jwtService;

    private final UUID proprietaireId = UUID.randomUUID();
    private final UUID autreAlumniId = UUID.randomUUID();
    private final String url = "/v1/alumni/" + proprietaireId;

    private AlumniProfile mockProfile() {
        return new AlumniProfile()
            .id(proprietaireId)
            .userId(proprietaireId)
            .firstName("Mouhamed")
            .lastName("Sow")
            .email("mouhamed@uasz.sn");
    }

    @BeforeEach
    void tokens() {
        when(jwtService.parse(anyString())).thenReturn(Optional.empty());
        // Le propriétaire
        when(jwtService.parse("proprio")).thenReturn(Optional.of(new AuthenticatedUser(proprietaireId, "a@uasz.sn", "ALUMNI")));
        // Un autre alumni
        when(jwtService.parse("autre")).thenReturn(Optional.of(new AuthenticatedUser(autreAlumniId, "b@uasz.sn", "ALUMNI")));
        // Un étudiant
        when(jwtService.parse("etudiant")).thenReturn(Optional.of(new AuthenticatedUser(proprietaireId, "e@uasz.sn", "ETUDIANT")));
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    // ==================== GET /alumni/{id} ====================

    @Test
    void getProfilExistantDonne200() throws Exception {
        when(profileService.getById(proprietaireId)).thenReturn(mockProfile());

        mvc.perform(as("etudiant", get(url)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Mouhamed"))
            .andExpect(jsonPath("$.email").value("mouhamed@uasz.sn"));
    }

    @Test
    void getProfilInexistantDonne404() throws Exception {
        when(profileService.getById(proprietaireId)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND, "ALUMNI_NOT_FOUND", "Alumni introuvable"));

        mvc.perform(as("etudiant", get(url)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ALUMNI_NOT_FOUND"));
    }

    @Test
    void getSansJetonDonne401() throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    // ==================== PUT /alumni/{id} ====================

    @Test
    void putParProprietaireDonne200() throws Exception {
        AlumniProfile updated = mockProfile().specialty("Génie Logiciel");
        when(profileService.update(eq(proprietaireId), any(AlumniProfileUpdateRequest.class), eq(proprietaireId)))
            .thenReturn(updated);

        mvc.perform(as("proprio", put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialty\":\"Génie Logiciel\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specialty").value("Génie Logiciel"));
    }

    @Test
    void putParAutreAlumniDonne403() throws Exception {
        // Le contrôleur passe l'ID de l'appelant au service.
        // C'est le service qui vérifie les droits, mais ici on simule le rejet du service.
        when(profileService.update(eq(proprietaireId), any(AlumniProfileUpdateRequest.class), eq(autreAlumniId)))
            .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Seul le propriétaire du profil peut le modifier"));

        mvc.perform(as("autre", put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialty\":\"Génie Logiciel\"}")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void putParEtudiantDonne403ViaPreAuthorize() throws Exception {
        // @PreAuthorize("hasRole('ALUMNI')") bloque avant même d'appeler le service
        mvc.perform(as("etudiant", put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialty\":\"Génie Logiciel\"}")))
            .andExpect(status().isForbidden());

        verify(profileService, never()).update(any(), any(), any());
    }

    @Test
    void putSansJetonDonne401() throws Exception {
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void putDonneesInvalidesDonne400() throws Exception {
        // Simule le rejet de validation du service (ex: année trop ancienne)
        when(profileService.update(eq(proprietaireId), any(AlumniProfileUpdateRequest.class), eq(proprietaireId)))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "graduationYear doit être compris entre 1900 et 2100"));

        mvc.perform(as("proprio", put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"graduationYear\":1800}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("graduationYear doit être compris entre 1900 et 2100"));
    }

    // ==================== GET /alumni (recherche) ====================

    @Test
    void searchAvecFiltresDonne200EtTransmetLesParametres() throws Exception {
        AlumniPagedResponse page = new AlumniPagedResponse()
            .content(List.of(mockProfile()))
            .totalElements(1).page(0).size(20);
        when(profileService.search(eq("sow"), eq(2023), eq("Génie Logiciel"), eq(0), eq(20)))
            .thenReturn(page);

        mvc.perform(as("etudiant", get("/v1/alumni")
                .param("query", "sow")
                .param("graduationYear", "2023")
                .param("specialty", "Génie Logiciel")
                .param("page", "0")
                .param("size", "20")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].firstName").value("Mouhamed"));
    }

    @Test
    void searchSansJetonDonne401() throws Exception {
        mvc.perform(get("/v1/alumni")).andExpect(status().isUnauthorized());
    }

    @Test
    void searchTailleInvalideDonne400() throws Exception {
        when(profileService.search(any(), any(), any(), any(), eq(500)))
            .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "size doit être compris entre 1 et 100"));

        mvc.perform(as("etudiant", get("/v1/alumni").param("size", "500")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void searchParSpecialiteSeule() throws Exception {
        when(profileService.search(any(), any(), eq("IA"), any(), any()))
            .thenReturn(new AlumniPagedResponse().content(List.of()).totalElements(0).page(0).size(20));

        mvc.perform(as("etudiant", get("/v1/alumni").param("specialty", "IA")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }
}
