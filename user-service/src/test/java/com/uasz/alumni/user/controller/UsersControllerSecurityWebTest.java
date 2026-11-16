package com.uasz.alumni.user.controller;

import static com.uasz.alumni.user.UserTestData.account;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.user.UserTestData;
import com.uasz.alumni.user.config.ApiPrefixConfig;
import com.uasz.alumni.user.config.SecurityConfig;
import com.uasz.alumni.user.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.user.jwt.JwtService;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.CurrentUser;
import com.uasz.alumni.user.security.RestSecurityErrorHandler;
import com.uasz.alumni.user.service.AuditService;
import com.uasz.alumni.user.service.PasswordService;
import com.uasz.alumni.user.service.RefreshTokenService;
import com.uasz.alumni.user.service.UserService;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Contrôle d'accès des routes /users de bout en bout côté web : filtre JWT réel, règles ADMINISTRATEUR de
 * SecurityConfig, règles "soi-même ou ADMINISTRATEUR" de UserService (réel) et format JSON des erreurs.
 * Seuls les dépôts, le parsing du jeton et les services annexes sont simulés.
 */
@WebMvcTest(controllers = UsersController.class, properties = {
        "security.api.prefix=/v1",
        "app.rate-limit.max-requests=1000",
        "app.rate-limit.login-max-requests=1000",
        "security.cors.allowed-origins=http://localhost:4200"})
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class, UserService.class,
        CurrentUser.class, RestSecurityErrorHandler.class})
class UsersControllerSecurityWebTest {

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
    @MockitoBean RefreshTokenService refreshTokens;
    @MockitoBean PasswordService passwords;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean AuditService audit;

    private UserAccount admin;
    private UserAccount alumni;
    private UserAccount otherAlumni;

    @BeforeEach
    void setUp() {
        admin = known(account(UUID.randomUUID(), "admin@uasz.sn", RoleName.ADMINISTRATEUR, UserStatus.ACTIF));
        alumni = known(account(UUID.randomUUID(), "awa.diop@uasz.sn", RoleName.ALUMNI, UserStatus.ACTIF));
        otherAlumni = known(account(UUID.randomUUID(), "moussa.sow@uasz.sn", RoleName.ALUMNI, UserStatus.ACTIF));
    }

    // ───── GET /users : ADMINISTRATEUR uniquement ─────

    @Test
    void listUsers_returns401WithoutToken() throws Exception {
        mvc.perform(get("/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listUsers_returns403ForNonAdmin() throws Exception {
        mvc.perform(as(alumni, get("/v1/users")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listUsers_returns200ForAdmin() throws Exception {
        when(users.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(alumni)));

        mvc.perform(as(admin, get("/v1/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("awa.diop@uasz.sn"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void listUsers_returns401WhenTokenBelongsToSuspendedAccount() throws Exception {
        UserAccount suspended = known(account(UUID.randomUUID(), "x@uasz.sn", RoleName.ADMINISTRATEUR,
                UserStatus.SUSPENDU));

        mvc.perform(as(suspended, get("/v1/users")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUser_returns403ForNonAdmin() throws Exception {
        mvc.perform(as(alumni, post("/v1/users")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"a@uasz.sn\",\"role\":\"ALUMNI\"}"))
                .andExpect(status().isForbidden());
        verify(users, never()).saveAndFlush(any());
    }

    // ───── GET/PUT /users/{id} : soi-même ou ADMINISTRATEUR ─────

    @Test
    void getUserById_returns200ForSelf() throws Exception {
        mvc.perform(as(alumni, get("/v1/users/" + alumni.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("awa.diop@uasz.sn"));
    }

    @Test
    void getUserById_returns403ForAnotherNonAdmin() throws Exception {
        mvc.perform(as(otherAlumni, get("/v1/users/" + alumni.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void getUserById_returns404ForAdminWhenUserIsUnknown() throws Exception {
        mvc.perform(as(admin, get("/v1/users/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void updateUser_returns200ForSelf() throws Exception {
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(as(alumni, put("/v1/users/" + alumni.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Aïssatou\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Aïssatou"));
    }

    @Test
    void updateUser_returns403ForAnotherNonAdmin() throws Exception {
        mvc.perform(as(otherAlumni, put("/v1/users/" + alumni.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Pirate\"}"))
                .andExpect(status().isForbidden());
        verify(users, never()).saveAndFlush(any());
    }

    // ───── PATCH /users/{id}/status et DELETE /users/{id} : ADMINISTRATEUR uniquement ─────

    @Test
    void updateUserStatus_returns403ForNonAdmin() throws Exception {
        mvc.perform(as(alumni, patch("/v1/users/" + otherAlumni.getId() + "/status"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDU\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUserStatus_returns200ForAdmin() throws Exception {
        mvc.perform(as(admin, patch("/v1/users/" + alumni.getId() + "/status"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIF"));
        verify(refreshTokens).revokeAll(alumni.getId());
    }

    @Test
    void deleteUser_returns403ForNonAdmin() throws Exception {
        mvc.perform(as(alumni, delete("/v1/users/" + otherAlumni.getId())))
                .andExpect(status().isForbidden());
        verify(users, never()).delete(any(UserAccount.class));
    }

    @Test
    void deleteUser_returns204ForAdmin() throws Exception {
        mvc.perform(as(admin, delete("/v1/users/" + alumni.getId())))
                .andExpect(status().isNoContent());
        verify(users).delete(alumni);
    }

    @Test
    void deleteUser_returns409WhenAdminTargetsHimself() throws Exception {
        mvc.perform(as(admin, delete("/v1/users/" + admin.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
    }

    // ───── utilitaires ─────

    /** Rend le compte connu du dépôt et associe un jeton "tok-<id>" à son id. */
    private UserAccount known(UserAccount account) {
        when(users.findById(account.getId())).thenReturn(Optional.of(account));
        when(jwtService.parseUserId("tok-" + account.getId())).thenReturn(Optional.of(account.getId()));
        return account;
    }

    private static MockHttpServletRequestBuilder as(UserAccount account, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer tok-" + account.getId());
    }
}
