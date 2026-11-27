package com.uasz.alumni.joboffer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.joboffer.config.ApiPrefixConfig;
import com.uasz.alumni.joboffer.config.SecurityConfig;
import com.uasz.alumni.joboffer.jwt.JwtService;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.service.CompanyHomeService;
import com.uasz.alumni.joboffer.spi.model.CompanyHomeSummary;
import com.uasz.alumni.joboffer.spi.model.OfferApplicationsCount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CompanyHomeController.class)
@Import({SecurityConfig.class, ApiPrefixConfig.class})
@TestPropertySource(properties = "security.api.prefix=/v1")
class CompanyHomeControllerTest {

    private static final String URL = "/v1/companies/me/home-summary";

    @Autowired MockMvc mvc;
    @MockitoBean CompanyHomeService service;
    @MockitoBean JwtService jwtService;

    private final UUID user = UUID.randomUUID();
    private final UUID company = UUID.randomUUID();
    private final UUID offerId = UUID.randomUUID();

    private RequestPostProcessor token(String role) {
        when(jwtService.parse("token")).thenReturn(
                Optional.of(new AuthenticatedUser(user, "u@uasz.sn", role, "Gainde 2000", company)));
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
            return request;
        };
    }

    private CompanyHomeSummary summary() {
        OfferApplicationsCount line = new OfferApplicationsCount();
        line.setOfferId(offerId);
        line.setTitle("Développeur Java");
        line.setApplications(4);
        CompanyHomeSummary summary = new CompanyHomeSummary();
        summary.setActiveOffers(1);
        summary.setApplicationsByOffer(List.of(line));
        summary.setProposalsSent(2);
        return summary;
    }

    @Test
    void uneEntrepriseRecoitSaSynthese() throws Exception {
        AuthenticatedUser caller = new AuthenticatedUser(user, "u@uasz.sn", "ENTREPRISE", "Gainde 2000", company);
        when(service.summaryFor(eq(caller))).thenReturn(summary());

        mvc.perform(get(URL).with(token("ENTREPRISE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeOffers").value(1))
                .andExpect(jsonPath("$.applicationsByOffer[0].offerId").value(offerId.toString()))
                .andExpect(jsonPath("$.applicationsByOffer[0].title").value("Développeur Java"))
                .andExpect(jsonPath("$.applicationsByOffer[0].applications").value(4))
                .andExpect(jsonPath("$.proposalsSent").value(2));
        verify(service).summaryFor(eq(caller));
    }

    @Test
    void sansJetonLaReponseEst401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verify(service, never()).summaryFor(any());
    }

    @Test
    void unAutreRoleRecoit403() throws Exception {
        for (String role : List.of("ETUDIANT", "ALUMNI", "ENSEIGNANT", "ADMINISTRATEUR")) {
            mvc.perform(get(URL).with(token(role))).andExpect(status().isForbidden());
        }
        verify(service, never()).summaryFor(any());
    }
}
