package com.uasz.alumni.alumnistudentcompany.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.alumnistudentcompany.config.ApiPrefixConfig;
import com.uasz.alumni.alumnistudentcompany.config.SecurityConfig;
import com.uasz.alumni.alumnistudentcompany.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
import com.uasz.alumni.alumnistudentcompany.jwt.JwtService;
import com.uasz.alumni.alumnistudentcompany.service.ProfileProvisioningService;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import com.uasz.alumni.alumnistudentcompany.service.CompanyService;

/**
 * {@code POST /internal/profiles} (ALUM-83) avec la vraie chaîne de sécurité et le vrai {@code JwtService} :
 * réservé aux jetons de service destinés à ce service (ADR-0010).
 */
@WebMvcTest(controllers = InternalController.class, properties = {
        "security.api.prefix=/v1",
        "jwt.secret=" + InternalControllerTest.SECRET,
        "jwt.issuer=alumni-uasz-user",
        "service-token.issuer=alumni-uasz-internal",
        "spring.application.name=alumni-student-company-service"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class, JwtService.class})
class InternalControllerTest {

    static final String SECRET = "dGVzdHMtb25seS1zZWNyZXQtMDEyMzQ1Njc4OS1hYmNkZWZnaGlqa2xtbm9w";
    static final String SELF = "alumni-student-company-service";
    static final UUID USER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    static final String BODY = """
            {"userId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","profileType":"ALUMNI",
             "firstName":"Awa","lastName":"Diop","email":"awa.diop@uasz.sn"}""";

    @Autowired MockMvc mvc;
    @MockitoBean ProfileProvisioningService provisioning;
    @MockitoBean CompanyService companies;

    private static String token(String issuer, String subject, String audience, String role) {
        var builder = Jwts.builder().issuer(issuer).subject(subject).claim("role", role)
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(300)));
        if (audience != null) {
            builder.audience().add(audience);
        }
        return builder.signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256).compact();
    }

    private static String serviceToken(String audience) {
        return token("alumni-uasz-internal", "user-service", audience, "SERVICE");
    }

    private static String userToken(String role) {
        return token("alumni-uasz-user", UUID.randomUUID().toString(), null, role);
    }

    private ResultActions provision(String token, String body) throws Exception {
        var req = post("/v1/internal/profiles").contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(req);
    }

    private static ProvisioningResultBuilder result() {
        return new ProvisioningResultBuilder();
    }

    private static final class ProvisioningResultBuilder {
        final UUID profileId = UUID.randomUUID();

        ProfileProvisioningService.Result created(boolean created) {
            return new ProfileProvisioningService.Result(
                    new ProfileProvisioningResponse(profileId, USER, ProvisionedProfileType.ALUMNI), created);
        }
    }

    @Test
    void createsTheProfileAnd201() throws Exception {
        when(provisioning.provision(any())).thenReturn(result().created(true));

        provision(serviceToken(SELF), BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER.toString()))
                .andExpect(jsonPath("$.profileType").value("ALUMNI"));
    }

    @Test
    void anExistingProfileIs200() throws Exception {
        when(provisioning.provision(any())).thenReturn(result().created(false));

        provision(serviceToken(SELF), BODY).andExpect(status().isOk());
    }

    @Test
    void studentProfilesAre501ForNow() throws Exception {
        when(provisioning.provision(any())).thenThrow(new NotImplementedYetException("provisionProfile"));

        provision(serviceToken(SELF), BODY.replace("ALUMNI", "ETUDIANT"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    @Test
    void anIncompleteRequestIs400() throws Exception {
        provision(serviceToken(SELF), "{\"profileType\":\"ALUMNI\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(provisioning);
    }

    @Test
    void withoutTokenIs401() throws Exception {
        provision(null, BODY).andExpect(status().isUnauthorized());
    }

    @Test
    void aUserTokenIs403EvenForAnAdministrator() throws Exception {
        provision(userToken("ADMINISTRATEUR"), BODY).andExpect(status().isForbidden());
        provision(userToken("ALUMNI"), BODY).andExpect(status().isForbidden());
        verifyNoInteractions(provisioning);
    }

    @Test
    void aServiceTokenForAnotherServiceIs401() throws Exception {
        provision(serviceToken("notification-messaging-service"), BODY).andExpect(status().isUnauthorized());
    }

    @Test
    void aServiceTokenHasNoAccessToTheUserApi() throws Exception {
        mvc.perform(get("/v1/alumni/" + UUID.randomUUID() + "/experiences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken(SELF)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unJetonDeServiceLitUneFiche() throws Exception {
        UUID id = UUID.randomUUID();
        when(companies.get(id)).thenReturn(new com.uasz.alumni.alumnistudentcompany.spi.model.Company()
                .name("Gainde 2000").isPartner(true));

        mvc.perform(get("/v1/internal/companies/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken(SELF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPartner").value(true));
    }

    @Test
    void unJwtUtilisateurNePeutPasLireLaFicheViaLAPIInterne() throws Exception {
        mvc.perform(get("/v1/internal/companies/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("ADMINISTRATEUR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(companies);
    }
}
