package com.uasz.alumni.alumnistudentcompany.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.uasz.alumni.alumnistudentcompany.config.ApiPrefixConfig;
import com.uasz.alumni.alumnistudentcompany.config.SecurityConfig;
import com.uasz.alumni.alumnistudentcompany.controller.AlumniController;
import com.uasz.alumni.alumnistudentcompany.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.alumnistudentcompany.jwt.JwtService;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.security.CurrentUserProvider;
import com.uasz.alumni.alumnistudentcompany.service.AlumniExperienceService;
import com.uasz.alumni.alumnistudentcompany.service.AlumniHomeSummaryService;
import com.uasz.alumni.alumnistudentcompany.service.AlumniProfileService;
import com.uasz.alumni.alumnistudentcompany.service.MessagingClient;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Tests dérivés du contrat pour {@code GET /alumni/me/home-summary} (ALUM-246, sous-tâche ALUM-247).
 *
 * <p>Codes HTTP et corps vérifiés contre contracts/alumni-student-company-service.yaml. Contrôleur, service de
 * synthèse et lecture de l'appelant réels ; seuls le décodage du jeton et la messagerie sont simulés.
 */
@WebMvcTest(controllers = AlumniController.class, properties = "security.api.prefix=/v1")
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class,
         AlumniHomeSummaryService.class, CurrentUserProvider.class})
class AlumniHomeSummaryContractTest {

    static final OpenApiContract CONTRACT = OpenApiContract.of("alumni-student-company-service");
    static final String PATH = "/alumni/me/home-summary";
    static final String URL = "/v1" + PATH;

    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean MessagingClient messaging;
    @MockitoBean AlumniProfileService profileService;
    @MockitoBean AlumniExperienceService experienceService;

    @BeforeEach
    void tokens() {
        when(jwt.parse(anyString())).thenReturn(Optional.empty());
        when(jwt.parse("alumni")).thenReturn(Optional.of(new AuthenticatedUser(UUID.randomUUID(), "a@uasz.sn", "ALUMNI")));
        for (String role : new String[] {"ETUDIANT", "ENSEIGNANT", "ENTREPRISE", "ADMINISTRATEUR"}) {
            when(jwt.parse(role)).thenReturn(Optional.of(new AuthenticatedUser(UUID.randomUUID(), "x@uasz.sn", role)));
        }
        when(messaging.unreadMessages("alumni")).thenReturn(5);
    }

    private MvcResult call(String token) throws Exception {
        MockHttpServletRequestBuilder req = get(URL);
        if (token != null) {
            req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(req).andReturn();
    }

    private static Object json(MvcResult r) throws Exception {
        String content = r.getResponse().getContentAsString();
        return content.isBlank() ? null : JsonPath.parse(content).json();
    }

    /** Le code renvoyé doit être déclaré par le contrat, et le corps conforme au schéma attendu. */
    private static void assertConforms(MvcResult r, String expectedSchema) throws Exception {
        int status = r.getResponse().getStatus();
        assertThat(CONTRACT.declaredStatuses(PATH, "GET"))
                .as("GET %s a renvoyé %d, code non déclaré dans le contrat", PATH, status)
                .contains(status);
        assertThat(CONTRACT.violations(json(r), expectedSchema)).as("corps de la réponse " + status).isEmpty();
    }

    @Test
    void alumniConnecte_200AvecUnAlumniHomeSummary() throws Exception {
        MvcResult r = call("alumni");

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertConforms(r, "AlumniHomeSummary");
        assertThat(JsonPath.<Map<String, Object>>read(r.getResponse().getContentAsString(), "$"))
                .containsEntry("unreadMessages", 5)
                .containsEntry("recommendationsGiven", 0)
                .containsEntry("pendingRequests", 0);
        verify(messaging).unreadMessages("alumni"); // le jeton de l'appelant est propagé, rien d'autre
    }

    @Test
    void sansJeton_401() throws Exception {
        MvcResult r = call(null);

        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertConforms(r, "ErrorResponse");
        verify(messaging, never()).unreadMessages(anyString());
    }

    @Test
    void jetonInvalide_401() throws Exception {
        MvcResult r = call("jeton-invalide");

        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertConforms(r, "ErrorResponse");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ETUDIANT", "ENSEIGNANT", "ENTREPRISE", "ADMINISTRATEUR"})
    void autreRole_403(String role) throws Exception {
        MvcResult r = call(role);

        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertConforms(r, "ErrorResponse");
        verify(messaging, never()).unreadMessages(anyString());
    }
}
