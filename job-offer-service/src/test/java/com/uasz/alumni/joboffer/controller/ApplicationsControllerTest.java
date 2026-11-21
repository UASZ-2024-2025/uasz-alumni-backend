package com.uasz.alumni.joboffer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.joboffer.config.ApiPrefixConfig;
import com.uasz.alumni.joboffer.config.SecurityConfig;
import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.jwt.JwtService;
import com.uasz.alumni.joboffer.model.JobApplication;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.service.ApplicationReviewService;
import com.uasz.alumni.joboffer.service.ApplicationReviewService.Caller;
import com.uasz.alumni.joboffer.service.ApplicationSubmissionService;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ApplicationsController.class)
@Import({SecurityConfig.class, ApiPrefixConfig.class})
@TestPropertySource(properties = "security.api.prefix=/v1")
class ApplicationsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ApplicationReviewService review;
    @MockitoBean ApplicationSubmissionService submissions;
    @MockitoBean JwtService jwtService;

    private final UUID recruiter = UUID.randomUUID();
    private final Offer offer = new Offer("Développeur Java", OfferType.JOB, "desc", UUID.randomUUID(), recruiter, "Dakar");
    private final JobApplication application = new JobApplication(offer, UUID.randomUUID(), "Awa Ndiaye", null, null);

    /** Jeton « token » reconnu par le JwtService simulé comme l'utilisateur et le rôle donnés (filtre ALUM-28 réel). */
    private RequestPostProcessor token(UUID user, String role) {
        when(jwtService.parse("token")).thenReturn(Optional.of(new AuthenticatedUser(user, "u@uasz.sn", role)));
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
            return request;
        };
    }

    @Test
    void lAuteurListeLesCandidaturesDeSonOffre() throws Exception {
        when(review.listForOffer(eq(offer.getId()), any())).thenReturn(List.of(application));

        mvc.perform(get("/v1/offers/" + offer.getId() + "/applications").with(token(recruiter, "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(application.getId().toString()))
                .andExpect(jsonPath("$[0].offerTitle").value("Développeur Java"))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
        verify(review).listForOffer(eq(offer.getId()), eq(new Caller(recruiter, false)));
    }

    @Test
    void unAdministrateurEstReconnuCommeTel() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(review.listForOffer(eq(offer.getId()), any())).thenReturn(List.of());

        mvc.perform(get("/v1/offers/" + offer.getId() + "/applications").with(token(adminId, "ADMINISTRATEUR")))
                .andExpect(status().isOk());
        verify(review).listForOffer(eq(offer.getId()), eq(new Caller(adminId, true)));
    }

    @Test
    void lesCandidaturesNeSontPasPubliques() throws Exception {
        mvc.perform(get("/v1/offers/" + offer.getId() + "/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lAuteurChangeLeStatut() throws Exception {
        application.changeStatus(ApplicationStatus.UNDER_REVIEW);
        when(review.changeStatus(eq(application.getId()), eq(ApplicationStatus.UNDER_REVIEW), any()))
                .thenReturn(application);

        mvc.perform(patch("/v1/applications/" + application.getId() + "/status").with(token(recruiter, "ALUMNI"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void unNonAuteurRecoit403AuFormatErrorResponse() throws Exception {
        when(review.changeStatus(any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "interdit"));

        mvc.perform(patch("/v1/applications/" + application.getId() + "/status").with(token(UUID.randomUUID(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void statutManquantDonne400() throws Exception {
        mvc.perform(patch("/v1/applications/" + application.getId() + "/status").with(token(recruiter, "ALUMNI"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(review, never()).changeStatus(any(), any(), any());
    }

    @Test
    void unEtudiantPostule_201() throws Exception {
        UUID etudiant = UUID.randomUUID();
        when(submissions.submit(eq(offer.getId()), any(), any())).thenReturn(application);

        mvc.perform(post("/v1/offers/" + offer.getId() + "/applications").with(token(etudiant, "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + etudiant + "\",\"coverLetter\":\"Bonjour\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(application.getId().toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void lEtudiantListeSesCandidatures_pagine() throws Exception {
        UUID etudiant = UUID.randomUUID();
        when(submissions.listMine(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(application), PageRequest.of(0, 20), 1));

        mvc.perform(get("/v1/applications").with(token(etudiant, "ETUDIANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].offerTitle").value("Développeur Java"));
    }

    @Test
    void sansJetonLeSuiviEstRefuse() throws Exception {
        mvc.perform(get("/v1/applications")).andExpect(status().isUnauthorized());
    }
}
