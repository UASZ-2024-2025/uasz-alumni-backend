package com.uasz.alumni.joboffer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.joboffer.config.ApiPrefixConfig;
import com.uasz.alumni.joboffer.config.SecurityConfig;
import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.jwt.JwtService;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.service.OfferService;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(OffersController.class)
@Import({SecurityConfig.class, ApiPrefixConfig.class})
@TestPropertySource(properties = "security.api.prefix=/v1")
class OffersControllerTest {

    private static final String BODY = """
            {"title":"Développeur Java","type":"JOB","description":"Spring Boot","companyId":"%s",
             "location":"Dakar","requiredSkills":["Java"]}""";

    @Autowired MockMvc mvc;
    @MockitoBean OfferService service;
    @MockitoBean JwtService jwtService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final Offer offer = new Offer("Développeur Java", OfferType.JOB, "Spring Boot", companyId, author, "Dakar");

    private RequestPostProcessor token(UUID user, String role, UUID company) {
        when(jwtService.parse("token")).thenReturn(
                Optional.of(new AuthenticatedUser(user, "u@uasz.sn", role, "Awa Diop", company)));
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
            return request;
        };
    }

    @Test
    void uneEntrepriseValideePublieEtRecoit201() throws Exception {
        when(service.create(any(), any())).thenReturn(offer);

        mvc.perform(post("/v1/offers").with(token(author, "ENTREPRISE", companyId))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(companyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(offer.getId().toString()))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
        verify(service).create(any(), eq(new AuthenticatedUser(author, "u@uasz.sn", "ENTREPRISE", "Awa Diop", companyId)));
    }

    @Test
    void laPublicationDUneEntrepriseNonValideeEstRefusee403() throws Exception {
        when(service.create(any(), any())).thenThrow(
                new ApiException(HttpStatus.FORBIDDEN, "COMPANY_NOT_VALIDATED", "non validée"));

        mvc.perform(post("/v1/offers").with(token(author, "ENTREPRISE", companyId))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(companyId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_VALIDATED"));
    }

    @Test
    void publierSansJetonDonne401() throws Exception {
        mvc.perform(post("/v1/offers").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(companyId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unCorpsSansTitreDonne400() throws Exception {
        mvc.perform(post("/v1/offers").with(token(author, "ENTREPRISE", companyId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"JOB\",\"description\":\"d\",\"companyId\":\"" + companyId
                                + "\",\"location\":\"Dakar\",\"requiredSkills\":[\"Java\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unNonAuteurRecoit403SurPutPatchEtDelete() throws Exception {
        ApiException forbidden = new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "interdit");
        when(service.update(eq(offer.getId()), any(), any())).thenThrow(forbidden);
        when(service.changeStatus(eq(offer.getId()), any(), any())).thenThrow(forbidden);
        doThrow(forbidden).when(service).delete(eq(offer.getId()), any());
        RequestPostProcessor other = token(UUID.randomUUID(), "ENTREPRISE", UUID.randomUUID());

        mvc.perform(put("/v1/offers/" + offer.getId()).with(other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(patch("/v1/offers/" + offer.getId() + "/status").with(other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/v1/offers/" + offer.getId()).with(other)).andExpect(status().isForbidden());
    }

    @Test
    void lAuteurChangeLeStatutEtSupprime() throws Exception {
        offer.changeStatus(OfferStatus.CLOSED);
        when(service.changeStatus(eq(offer.getId()), eq(OfferStatus.CLOSED), any())).thenReturn(offer);

        mvc.perform(patch("/v1/offers/" + offer.getId() + "/status").with(token(author, "ENTREPRISE", companyId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(delete("/v1/offers/" + offer.getId()).with(token(author, "ENTREPRISE", companyId)))
                .andExpect(status().isNoContent());
    }

    @Test
    void laConsultationEstPubliqueSansJeton() throws Exception {
        when(service.search(any(), isNull(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(offer)));
        when(service.get(eq(offer.getId()), isNull())).thenReturn(offer);

        mvc.perform(get("/v1/offers?query=java&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Développeur Java"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/v1/offers/" + offer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Dakar"));
    }

    @Test
    void uneOffreInconnueDonne404() throws Exception {
        when(service.get(any(), any())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "introuvable"));

        mvc.perform(get("/v1/offers/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
    }
}
