package com.uasz.alumni.joboffer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.joboffer.config.ApiPrefixConfig;
import com.uasz.alumni.joboffer.config.SecurityConfig;
import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.JobApplication;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.service.ApplicationReviewService;
import com.uasz.alumni.joboffer.service.ApplicationReviewService.Caller;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
    @MockitoBean JwtDecoder jwtDecoder;

    private final UUID recruiter = UUID.randomUUID();
    private final Offer offer = new Offer("Développeur Java", OfferType.JOB, "desc", UUID.randomUUID(), recruiter, "Dakar");
    private final JobApplication application = new JobApplication(offer, UUID.randomUUID(), "Awa Ndiaye", null, null);

    private RequestPostProcessor token(UUID user, String role) {
        return jwt().jwt(j -> j.subject(user.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void lAuteurListeLesCandidaturesDeSonOffre() throws Exception {
        when(review.listForOffer(eq(offer.getId()), any())).thenReturn(List.of(application));

        mvc.perform(get("/v1/offers/" + offer.getId() + "/applications").with(token(recruiter, "ALUMNI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(application.getId().toString()))
                .andExpect(jsonPath("$[0].offerTitle").value("Développeur Java"))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
        verify(review).listForOffer(eq(offer.getId()), eq(new Caller(recruiter, false, "token")));
    }

    @Test
    void unAdministrateurEstReconnuCommeTel() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(review.listForOffer(eq(offer.getId()), any())).thenReturn(List.of());

        mvc.perform(get("/v1/offers/" + offer.getId() + "/applications").with(token(adminId, "ADMINISTRATEUR")))
                .andExpect(status().isOk());
        verify(review).listForOffer(eq(offer.getId()), eq(new Caller(adminId, true, "token")));
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
}
