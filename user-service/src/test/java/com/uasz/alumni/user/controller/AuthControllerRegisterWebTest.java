package com.uasz.alumni.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.user.config.ApiPrefixConfig;
import com.uasz.alumni.user.config.AppProperties;
import com.uasz.alumni.user.config.SecurityConfig;
import com.uasz.alumni.user.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.user.model.EmailConfirmationToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.util.Optional;
import com.uasz.alumni.user.repository.EmailConfirmationTokenRepository;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.exceptions.InvalidCredentialsException;
import com.uasz.alumni.user.jwt.JwtService;
import com.uasz.alumni.user.security.CurrentUser;
import com.uasz.alumni.user.security.RestSecurityErrorHandler;
import com.uasz.alumni.user.service.AuthService;
import com.uasz.alumni.user.service.AuthSession;
import com.uasz.alumni.user.service.PasswordService;
import com.uasz.alumni.user.service.RegistrationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Teste POST /v1/auth/register de bout en bout côté web : désérialisation, validation du contrat,
 * RegistrationService réel (dépôts simulés) et format JSON des erreurs (GlobalExceptionHandler).
 */
@WebMvcTest(controllers = AuthController.class, properties = {
        "security.api.prefix=/v1",
        "app.rate-limit.max-requests=1000",
        "app.rate-limit.login-max-requests=1000",
        "security.cors.allowed-origins=http://localhost:4200"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class, RegistrationService.class,
        CurrentUser.class, RestSecurityErrorHandler.class})
class AuthControllerRegisterWebTest {

    private static final String URL = "/v1/auth/register";

    @TestConfiguration
    static class Config {
        @Bean
        AppProperties appProperties() {
            return new AppProperties("http://localhost:4200", "no-reply@alumni-uasz.sn", Duration.ofHours(24));
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-04T20:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;

    @MockitoBean UserAccountRepository users;
    @MockitoBean EmailConfirmationTokenRepository tokens;
    @MockitoBean PasswordEncoder passwordEncoder;
    // requis par AuthController (AL-19/AL-20) et SecurityConfig ; non testés ici
    @MockitoBean AuthService authService;
    @MockitoBean PasswordService passwordService;
    @MockitoBean JwtService jwtService;

    @Test
    void register_returns201() throws Exception {
        when(users.existsByEmail("awa.diop@uasz.sn")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse1")).thenReturn("HASH");
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        send(body("Diop", "Awa", "awa.diop@uasz.sn", "motdepasse1", "ALUMNI"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("awa.diop@uasz.sn"))
                .andExpect(jsonPath("$.status").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_returns409WhenEmailAlreadyUsed() throws Exception {
        when(users.existsByEmail("awa.diop@uasz.sn")).thenReturn(true);

        send(body("Diop", "Awa", "awa.diop@uasz.sn", "motdepasse1", "ALUMNI"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"))
                .andExpect(jsonPath("$.message").value("Cette adresse email est déjà utilisée."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void register_returns400WhenFieldIsMissing() throws Exception {
        send("{\"lastName\":\"Diop\",\"email\":\"awa.diop@uasz.sn\",\"password\":\"motdepasse1\",\"role\":\"ALUMNI\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("firstName")))
                .andExpect(jsonPath("$.message", not(containsString("com.uasz"))));
    }

    @Test
    void register_returns400WhenPasswordIsTooShort() throws Exception {
        send(body("Diop", "Awa", "awa.diop@uasz.sn", "court", "ALUMNI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("com.uasz"))));
    }

    @Test
    void register_returns400WhenFirstNameIsBlank() throws Exception {
        send(body("Diop", "   ", "awa.diop@uasz.sn", "motdepasse1", "ALUMNI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_DATA"))
                .andExpect(jsonPath("$.message").value("Le prénom est obligatoire."));
    }

    @Test
    void register_returns400WhenPasswordExceeds72Bytes() throws Exception {
        send(body("Diop", "Awa", "awa.diop@uasz.sn", "a".repeat(80), "ALUMNI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_DATA"))
                .andExpect(jsonPath("$.message", containsString("72 octets")));
    }

    @Test
    void register_returns400WithoutJavaClassNameWhenRoleIsUnknown() throws Exception {
        send(body("Diop", "Awa", "awa.diop@uasz.sn", "motdepasse1", "PIRATE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message", not(containsString("com.uasz"))))
                .andExpect(jsonPath("$.message", not(containsString("RoleName"))));
    }

    @Test
    void register_returns400WhenBodyIsNotJson() throws Exception {
        send("pas du json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ───── confirm-email, resend-confirmation, login (endpoints voisins, même contexte) ─────

    @Test
    void confirmEmail_returns200AndActivatesTheAccount() throws Exception {
        Instant now = Instant.parse("2026-11-04T20:00:00Z");
        UserAccount account = new UserAccount("awa.diop@uasz.sn", "HASH", "Awa", "Diop",
                RoleName.ALUMNI, UserStatus.EN_ATTENTE, now);
        EmailConfirmationToken token = new EmailConfirmationToken(account, "h".repeat(64), now, now.plusSeconds(3600));
        when(tokens.findByTokenHashWithUser(any())).thenReturn(Optional.of(token));

        sendTo("/v1/auth/confirm-email", "{\"token\":\"" + "a".repeat(43) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Compte activé avec succès."));
    }

    @Test
    void confirmEmail_returns400WhenTokenIsUnknown() throws Exception {
        when(tokens.findByTokenHashWithUser(any())).thenReturn(Optional.empty());

        sendTo("/v1/auth/confirm-email", "{\"token\":\"" + "a".repeat(43) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    void resendConfirmation_returns200WithoutRevealingUnknownAddresses() throws Exception {
        when(users.findByEmail("inconnu@uasz.sn")).thenReturn(Optional.empty());

        sendTo("/v1/auth/resend-confirmation", "{\"email\":\"inconnu@uasz.sn\"}")
                .andExpect(status().isOk());
    }

    @Test
    void resendConfirmation_returns409WhenAccountIsAlreadyActive() throws Exception {
        Instant now = Instant.parse("2026-11-04T20:00:00Z");
        UserAccount active = new UserAccount("awa.diop@uasz.sn", "HASH", "Awa", "Diop",
                RoleName.ALUMNI, UserStatus.ACTIF, now);
        when(users.findByEmail("awa.diop@uasz.sn")).thenReturn(Optional.of(active));

        sendTo("/v1/auth/resend-confirmation", "{\"email\":\"awa.diop@uasz.sn\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_ACTIVE"));
    }

    @Test
    void login_returns200WithTokens() throws Exception {
        Instant now = Instant.parse("2026-11-04T20:00:00Z");
        UserAccount account = new UserAccount("awa.diop@uasz.sn", "HASH", "Awa", "Diop",
                RoleName.ALUMNI, UserStatus.ACTIF, now);
        when(authService.login("awa.diop@uasz.sn", "motdepasse1"))
                .thenReturn(new AuthSession("ACCESS", "REFRESH", 900L, account));

        sendTo("/v1/auth/login", "{\"email\":\"awa.diop@uasz.sn\",\"password\":\"motdepasse1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("ACCESS"))
                .andExpect(jsonPath("$.refreshToken").value("REFRESH"))
                .andExpect(jsonPath("$.user.email").value("awa.diop@uasz.sn"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void login_returns401WhenCredentialsAreInvalid() throws Exception {
        when(authService.login("awa.diop@uasz.sn", "mauvais1")).thenThrow(new InvalidCredentialsException());

        sendTo("/v1/auth/login", "{\"email\":\"awa.diop@uasz.sn\",\"password\":\"mauvais1\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ───── erreurs non prévues : jamais de détail technique côté client ─────

    @Test
    void unexpectedError_returns500WithoutTechnicalDetails() throws Exception {
        when(users.existsByEmail("awa.diop@uasz.sn")).thenThrow(new IllegalStateException("détail interne secret"));

        send(body("Diop", "Awa", "awa.diop@uasz.sn", "motdepasse1", "ALUMNI"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("secret"))));
    }

    @Test
    void wrongHttpMethod_returns405WithReadableMessage() throws Exception {
        // Utilisateur authentifié : sinon SecurityConfig répond 401 avant que Spring MVC ne produise le 405
        // (seul POST est public sur /auth/register).
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(URL)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("x")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message", not(containsString("com.uasz"))));
    }

    private ResultActions sendTo(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions send(String json) throws Exception {
        return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String lastName, String firstName, String email, String password, String role) {
        return "{\"lastName\":\"" + lastName + "\",\"firstName\":\"" + firstName + "\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}";
    }
}
