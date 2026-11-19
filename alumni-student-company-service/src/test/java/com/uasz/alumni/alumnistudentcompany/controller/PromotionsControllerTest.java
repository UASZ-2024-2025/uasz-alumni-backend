package com.uasz.alumni.alumnistudentcompany.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.service.PromotionService;
import com.uasz.alumni.alumnistudentcompany.spi.model.Promotion;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
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

/** Routes /promotions (ALUM-88) : filtre JWT réel, seul le parsing du jeton est simulé. */
@WebMvcTest(controllers = PromotionsController.class, properties = "security.api.prefix=/v1")
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class PromotionsControllerTest {

    private static final String BODY = "{\"academicYear\":2025,\"specialty\":\"Master Génie Logiciel\"}";

    @Autowired MockMvc mvc;
    @MockitoBean PromotionService service;
    @MockitoBean JwtService jwtService;

    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@uasz.sn", "ADMINISTRATEUR");
    private final AuthenticatedUser alumni = new AuthenticatedUser(UUID.randomUUID(), "a@uasz.sn", "ALUMNI");
    private final AuthenticatedUser etudiant = new AuthenticatedUser(UUID.randomUUID(), "e@uasz.sn", "ETUDIANT");

    @BeforeEach
    void tokens() {
        when(jwtService.parse(anyString())).thenReturn(Optional.empty());
        when(jwtService.parse("admin")).thenReturn(Optional.of(admin));
        when(jwtService.parse("alumni")).thenReturn(Optional.of(alumni));
        when(jwtService.parse("etudiant")).thenReturn(Optional.of(etudiant));
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder create(String body) {
        return post("/v1/promotions").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void lAdministrateurCreeUnePromotionEt201() throws Exception {
        when(service.create(any())).thenReturn(new Promotion().id(UUID.randomUUID()).academicYear(2025)
                .specialty("Master Génie Logiciel").graduateCount(0));

        mvc.perform(as("admin", create(BODY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.academicYear").value(2025))
                .andExpect(jsonPath("$.specialty").value("Master Génie Logiciel"))
                .andExpect(jsonPath("$.graduateCount").value(0));
    }

    @Test
    void seulLAdministrateurPeutCreerUnePromotion() throws Exception {
        mvc.perform(as("alumni", create(BODY))).andExpect(status().isForbidden());
        mvc.perform(as("etudiant", create(BODY))).andExpect(status().isForbidden());
        verify(service, never()).create(any());
    }

    @Test
    void sansJetonDonne401() throws Exception {
        mvc.perform(create(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/promotions")).andExpect(status().isUnauthorized());
    }

    @Test
    void anneeOuSpecialiteManquanteDonne400() throws Exception {
        mvc.perform(as("admin", create("{\"specialty\":\"Réseaux\"}"))).andExpect(status().isBadRequest());
        mvc.perform(as("admin", create("{\"academicYear\":2025}"))).andExpect(status().isBadRequest());
        verify(service, never()).create(any());
    }

    @Test
    void unDoublonDonne409AuFormatErrorResponse() throws Exception {
        when(service.create(any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "PROMOTION_ALREADY_EXISTS",
                "La promotion Master Génie Logiciel 2025 existe déjà."));

        mvc.perform(as("admin", create(BODY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_ALREADY_EXISTS"));
    }

    @Test
    void tousLesUtilisateursConnectesListentLesPromotions() throws Exception {
        when(service.list()).thenReturn(List.of(new Promotion().academicYear(2025).specialty("Réseaux").graduateCount(2)));

        mvc.perform(as("etudiant", get("/v1/promotions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].specialty").value("Réseaux"))
                .andExpect(jsonPath("$[0].graduateCount").value(2));
    }

    @Test
    void listeDesDiplomesEt404SiLaPromotionNExistePas() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.graduates(id)).thenReturn(List.of(new StudentProfile().firstName("Awa").lastName("Diop")));
        mvc.perform(as("alumni", get("/v1/promotions/" + id + "/graduates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Diop"));

        UUID unknown = UUID.randomUUID();
        when(service.graduates(unknown)).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "PROMOTION_NOT_FOUND", "Promotion introuvable."));
        mvc.perform(as("alumni", get("/v1/promotions/" + unknown + "/graduates")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
    }
}
