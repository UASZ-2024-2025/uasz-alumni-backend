package com.uasz.alumni.alumnistudentcompany.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.config.ApiPrefixConfig;
import com.uasz.alumni.alumnistudentcompany.config.SecurityConfig;
import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.alumnistudentcompany.jwt.JwtService;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.service.CompanyService;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyPagedResponse;
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

/** Routes /companies côté web : filtre JWT réel, seul le parsing du jeton est simulé. */
@WebMvcTest(controllers = CompaniesController.class, properties = "security.api.prefix=/v1")
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class CompaniesControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CompanyService service;
    @MockitoBean JwtService jwtService;

    private final UUID companyId = UUID.randomUUID();
    private final AuthenticatedUser alumni = new AuthenticatedUser(UUID.randomUUID(), "a@uasz.sn", "ALUMNI");
    private final AuthenticatedUser etudiant = new AuthenticatedUser(UUID.randomUUID(), "e@uasz.sn", "ETUDIANT");
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@uasz.sn", "ADMINISTRATEUR");

    private static final String VALID_BODY =
            "{\"name\":\"Gainde 2000\",\"sector\":\"Informatique\",\"contactEmail\":\"contact@gainde.sn\"}";

    @BeforeEach
    void tokens() {
        when(jwtService.parse(anyString())).thenReturn(Optional.empty());
        when(jwtService.parse("alumni")).thenReturn(Optional.of(alumni));
        when(jwtService.parse("etudiant")).thenReturn(Optional.of(etudiant));
        when(jwtService.parse("admin")).thenReturn(Optional.of(admin));
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void alumniCreeUneFicheEt201() throws Exception {
        when(service.create(any(), eq(alumni))).thenReturn(new Company().name("Gainde 2000").ownerId(alumni.id()));

        mvc.perform(as("alumni", json(post("/v1/companies"), VALID_BODY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gainde 2000"))
                .andExpect(jsonPath("$.ownerId").value(alumni.id().toString()));
    }

    @Test
    void unEtudiantNePeutPasCreerUneFiche() throws Exception {
        mvc.perform(as("etudiant", json(post("/v1/companies"), VALID_BODY))).andExpect(status().isForbidden());
        verify(service, never()).create(any(), any());
    }

    @Test
    void sansJetonOuAvecJetonInvalideDonne401() throws Exception {
        mvc.perform(json(post("/v1/companies"), VALID_BODY)).andExpect(status().isUnauthorized());
        mvc.perform(as("inconnu", get("/v1/companies"))).andExpect(status().isUnauthorized());
    }

    @Test
    void unCorpsSansSecteurDonne400() throws Exception {
        mvc.perform(as("alumni", json(post("/v1/companies"), "{\"name\":\"X\",\"contactEmail\":\"x@y.sn\"}")))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any(), any());
    }

    @Test
    void laRechercheTransmetFiltresEtPagination() throws Exception {
        CompanyPagedResponse page = new CompanyPagedResponse();
        page.setContent(List.of(new Company().name("Gainde 2000")));
        page.setTotalElements(1);
        page.setPage(1);
        page.setSize(10);
        when(service.search("tech", true, 1, 10)).thenReturn(page);

        mvc.perform(as("etudiant", get("/v1/companies?query=tech&isPartner=true&page=1&size=10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Gainde 2000"))
                .andExpect(jsonPath("$.size").value(10));
        verify(service).search("tech", true, 1, 10);
    }

    @Test
    void uneFicheInconnueDonne404() throws Exception {
        when(service.get(companyId)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "introuvable"));

        mvc.perform(as("alumni", get("/v1/companies/" + companyId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    void unNonProprietaireRecoit403SurPut() throws Exception {
        when(service.update(eq(companyId), any(), eq(alumni)))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "refusé"));

        mvc.perform(as("alumni", json(put("/v1/companies/" + companyId), "{\"description\":\"x\"}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void lAdministrateurPasseIsPartnerEt200() throws Exception {
        when(service.update(eq(companyId), any(), eq(admin))).thenReturn(new Company().name("Gainde 2000").isPartner(true));

        mvc.perform(as("admin", json(put("/v1/companies/" + companyId), "{\"isPartner\":true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPartner").value(true));
        verify(service).update(eq(companyId), any(), eq(admin));
    }
}
