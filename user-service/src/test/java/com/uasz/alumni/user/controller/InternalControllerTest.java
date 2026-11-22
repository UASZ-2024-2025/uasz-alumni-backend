package com.uasz.alumni.user.controller;

import static com.uasz.alumni.user.UserTestData.account;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.user.UserTestData;
import com.uasz.alumni.user.config.ApiPrefixConfig;
import com.uasz.alumni.user.config.SecurityConfig;
import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.user.jwt.JwtService;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.RestSecurityErrorHandler;
import com.uasz.alumni.user.service.CompanyLinkService;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** {@code PUT /internal/users/{id}/company} (ALUM-168) : réservé aux jetons de service (ADR-0010). */
@WebMvcTest(controllers = InternalController.class, properties = {
        "security.api.prefix=/v1",
        "app.rate-limit.max-requests=1000",
        "app.rate-limit.login-max-requests=1000",
        "security.cors.allowed-origins=http://localhost:4200"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class, RestSecurityErrorHandler.class})
class InternalControllerTest {

    @TestConfiguration
    static class Config {
        @Bean
        Clock clock() {
            return UserTestData.clock();
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository users;
    @MockitoBean JwtService jwtService;
    @MockitoBean CompanyLinkService links;

    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void tokens() {
        when(jwtService.parseServiceCaller("service")).thenReturn(Optional.of("alumni-student-company-service"));
        when(jwtService.parseUserId("admin")).thenReturn(Optional.of(adminId));
        when(users.findById(adminId)).thenReturn(
                Optional.of(account(adminId, "admin@uasz.sn", RoleName.ADMINISTRATEUR, UserStatus.ACTIF)));
    }

    private ResultActions link(String token, String body) throws Exception {
        var req = put("/v1/internal/users/" + userId + "/company")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(req);
    }

    private String body() {
        return "{\"companyId\":\"" + companyId + "\"}";
    }

    @Test
    void unJetonDeServiceRattacheLeCompteEtRenvoieLeCompanyId() throws Exception {
        UserAccount linked = account(userId, "rh@gainde.sn", RoleName.ENTREPRISE, UserStatus.ACTIF);
        linked.attachToCompany(companyId);
        when(links.linkCompany(userId, companyId)).thenReturn(linked);

        link("service", body())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()));
        verify(links).linkCompany(eq(userId), eq(companyId));
    }

    @Test
    void unConflitDuServiceRemonteEn409() throws Exception {
        when(links.linkCompany(any(), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "COMPANY_ALREADY_LINKED", "déjà"));

        link("service", body())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_ALREADY_LINKED"));
    }

    @Test
    void unJwtUtilisateurMemeAdministrateurRecoit403() throws Exception {
        link("admin", body()).andExpect(status().isForbidden());
        verifyNoInteractions(links);
    }

    @Test
    void sansJetonOuAvecJetonInconnuDonne401() throws Exception {
        link(null, body()).andExpect(status().isUnauthorized());
        link("inconnu", body()).andExpect(status().isUnauthorized());
        verifyNoInteractions(links);
    }

    @Test
    void unCorpsSansCompanyIdDonne400() throws Exception {
        link("service", "{}").andExpect(status().isBadRequest());
        verifyNoInteractions(links);
    }
}
