package com.uasz.alumni.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uasz.alumni.user.model.RoleEntity;
import com.uasz.alumni.user.model.UserEntity;
import com.uasz.alumni.user.repository.RoleRepository;
import com.uasz.alumni.user.repository.UserRepository;
import com.uasz.alumni.user.spi.model.LoginRequest;
import com.uasz.alumni.user.spi.model.RegisterRequest;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        userRepository.deleteAll();
        roleRepository.deleteAll();

        roleRepository.save(new RoleEntity(RoleName.ETUDIANT, "Etudiant", Set.of("USER_SELF")));
        roleRepository.save(new RoleEntity(RoleName.ADMINISTRATEUR, "Admin", Set.of("USER_ALL")));

        // Utilisateur actif
        UserEntity activeUser = new UserEntity();
        activeUser.setId(UUID.randomUUID());
        activeUser.setFirstName("Ibrahima");
        activeUser.setLastName("Sane");
        activeUser.setEmail("actif@uasz.sn");
        activeUser.setPasswordHash(passwordEncoder.encode("Secret123!"));
        activeUser.setRole(roleRepository.findById(RoleName.ETUDIANT).orElseThrow());
        activeUser.setStatus(UserStatus.ACTIF);
        userRepository.save(activeUser);

        // Utilisateur suspendu
        UserEntity suspendedUser = new UserEntity();
        suspendedUser.setId(UUID.randomUUID());
        suspendedUser.setFirstName("Ousmane");
        suspendedUser.setLastName("Ba");
        suspendedUser.setEmail("suspendu@uasz.sn");
        suspendedUser.setPasswordHash(passwordEncoder.encode("Secret123!"));
        suspendedUser.setRole(roleRepository.findById(RoleName.ETUDIANT).orElseThrow());
        suspendedUser.setStatus(UserStatus.SUSPENDU);
        userRepository.save(suspendedUser);

        // Utilisateur en attente
        UserEntity pendingUser = new UserEntity();
        pendingUser.setId(UUID.randomUUID());
        pendingUser.setFirstName("Awa");
        pendingUser.setLastName("Gueye");
        pendingUser.setEmail("enattente@uasz.sn");
        pendingUser.setPasswordHash(passwordEncoder.encode("Secret123!"));
        pendingUser.setRole(roleRepository.findById(RoleName.ETUDIANT).orElseThrow());
        pendingUser.setStatus(UserStatus.EN_ATTENTE);
        userRepository.save(pendingUser);
    }

    @Test
    @DisplayName("POST /v1/auth/login - Connexion réussie pour un compte ACTIF")
    void login_activeUser_returns200AndTokens() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("actif@uasz.sn");
        request.setPassword("Secret123!");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("actif@uasz.sn")))
                .andExpect(jsonPath("$.user.status", is("ACTIF")));
    }

    @Test
    @DisplayName("POST /v1/auth/login - CRITÈRE D'ACCEPTATION : Un utilisateur suspendu ne peut plus se connecter (403)")
    void login_suspendedUser_returns403Forbidden() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("suspendu@uasz.sn");
        request.setPassword("Secret123!");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("POST /v1/auth/login - Un utilisateur avec compte non activé ne peut pas se connecter (403)")
    void login_pendingUser_returns403Forbidden() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("enattente@uasz.sn");
        request.setPassword("Secret123!");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("POST /v1/auth/login - Mot de passe incorrect renvoie 401 Unauthorized")
    void login_wrongPassword_returns401Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("actif@uasz.sn");
        request.setPassword("MauvaisMotDePasse!");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("POST /v1/auth/register - Inscription valide crée un compte EN_ATTENTE (201)")
    void register_validUser_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Cheikh");
        request.setLastName("Fall");
        request.setEmail("cheikh.fall@univ-zig.sn");
        request.setPassword("P@ssword123");
        request.setRole(RoleName.ETUDIANT);

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("cheikh.fall@univ-zig.sn")))
                .andExpect(jsonPath("$.status", is("EN_ATTENTE")))
                .andExpect(jsonPath("$.role", is("ETUDIANT")));
    }

    @Test
    @DisplayName("POST /v1/auth/register - Email existant renvoie 409 Conflict")
    void register_existingEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Autre");
        request.setLastName("Personne");
        request.setEmail("actif@uasz.sn");
        request.setPassword("P@ssword123");
        request.setRole(RoleName.ETUDIANT);

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")));
    }
}
