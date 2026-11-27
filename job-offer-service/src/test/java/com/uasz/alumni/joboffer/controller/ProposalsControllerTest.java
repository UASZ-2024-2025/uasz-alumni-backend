package com.uasz.alumni.joboffer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.model.ProposalEntity;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.service.ProposalService;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
import java.time.OffsetDateTime;
import java.util.List;
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

@WebMvcTest(ProposalsController.class)
@Import({SecurityConfig.class, ApiPrefixConfig.class})
@TestPropertySource(properties = "security.api.prefix=/v1")
class ProposalsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProposalService proposalService;
    @MockitoBean JwtService jwtService;

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final Offer offer = new Offer("Développeur Java", OfferType.JOB, "desc", UUID.randomUUID(), recruiterId, "Dakar");
    private final ProposalEntity proposal = new ProposalEntity(
            offer, studentId, "Awa Ndiaye", recruiterId, "Atos SN", "Rejoignez-nous", OffsetDateTime.now().plusDays(14));

    private RequestPostProcessor token(UUID userId, String role) {
        when(jwtService.parse("token")).thenReturn(Optional.of(new AuthenticatedUser(userId, "u@uasz.sn", role)));
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
            return request;
        };
    }

    @Test
    void propositionsNonPubliques() throws Exception {
        mvc.perform(get("/v1/proposals")).andExpect(status().isUnauthorized());
    }

    @Test
    void creerPropositionRetourne201() throws Exception {
        when(proposalService.create(any(), any())).thenReturn(proposal);

        mvc.perform(post("/v1/proposals").with(token(recruiterId, "ENTREPRISE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"offerId":"%s","studentId":"%s","message":"Rejoignez-nous"}
                                """.formatted(offer.getId(), studentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(proposal.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.offerTitle").value("Développeur Java"));
    }

    @Test
    void listerPropositionsRetourne200() throws Exception {
        when(proposalService.list(any(), any(), any(), any())).thenReturn(List.of(proposal));

        mvc.perform(get("/v1/proposals").with(token(studentId, "ETUDIANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(proposal.getId().toString()));
    }

    @Test
    void repondrePropositionRetourne200() throws Exception {
        proposal.respond(ProposalStatus.ACCEPTED);
        when(proposalService.updateStatus(eq(proposal.getId()), eq(ProposalStatus.ACCEPTED), any()))
                .thenReturn(proposal);

        mvc.perform(patch("/v1/proposals/" + proposal.getId() + "/status").with(token(studentId, "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void reponseParAutreUtilisateurRetourne403() throws Exception {
        when(proposalService.updateStatus(eq(proposal.getId()), eq(ProposalStatus.ACCEPTED), any()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Refusé"));

        mvc.perform(patch("/v1/proposals/" + proposal.getId() + "/status").with(token(UUID.randomUUID(), "ETUDIANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
