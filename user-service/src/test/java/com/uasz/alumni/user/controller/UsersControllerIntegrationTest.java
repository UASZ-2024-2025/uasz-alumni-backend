package com.uasz.alumni.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uasz.alumni.user.jwt.JwtTokenProvider;
import com.uasz.alumni.user.model.RoleEntity;
import com.uasz.alumni.user.model.UserEntity;
import com.uasz.alumni.user.repository.RoleRepository;
import com.uasz.alumni.user.repository.UserRepository;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserStatusUpdateRequest;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import java.time.LocalDate;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class UsersControllerIntegrationTest {

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

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private UserEntity adminUser;
    private UserEntity studentUser;
    private UserEntity otherStudentUser;
    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        userRepository.deleteAll();
        roleRepository.deleteAll();

        RoleEntity adminRole = roleRepository.save(new RoleEntity(RoleName.ADMINISTRATEUR, "Admin", Set.of("USER_ALL")));
        RoleEntity studentRole = roleRepository.save(new RoleEntity(RoleName.ETUDIANT, "Etudiant", Set.of("USER_SELF")));

        adminUser = new UserEntity();
        adminUser.setId(UUID.randomUUID());
        adminUser.setFirstName("Amadou");
        adminUser.setLastName("Diallo");
        adminUser.setEmail("admin@uasz.sn");
        adminUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        adminUser.setRole(adminRole);
        adminUser.setStatus(UserStatus.ACTIF);
        adminUser = userRepository.save(adminUser);

        studentUser = new UserEntity();
        studentUser.setId(UUID.randomUUID());
        studentUser.setFirstName("Fatou");
        studentUser.setLastName("Sow");
        studentUser.setEmail("fatou.sow@univ-zig.sn");
        studentUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        studentUser.setRole(studentRole);
        studentUser.setStatus(UserStatus.ACTIF);
        studentUser.setPhone("771234567");
        studentUser = userRepository.save(studentUser);

        otherStudentUser = new UserEntity();
        otherStudentUser.setId(UUID.randomUUID());
        otherStudentUser.setFirstName("Moussa");
        otherStudentUser.setLastName("Diop");
        otherStudentUser.setEmail("moussa.diop@univ-zig.sn");
        otherStudentUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        otherStudentUser.setRole(studentRole);
        otherStudentUser.setStatus(UserStatus.ACTIF);
        otherStudentUser = userRepository.save(otherStudentUser);

        adminToken = jwtTokenProvider.generateAccessToken(adminUser);
        studentToken = jwtTokenProvider.generateAccessToken(studentUser);
    }

    @Test
    @DisplayName("GET /v1/users - Succès pour un administrateur avec pagination et filtres")
    void listUsers_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "10")
                        .param("role", "ETUDIANT")
                        .param("status", "ACTIF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[0].role", is("ETUDIANT")));
    }

    @Test
    @DisplayName("GET /v1/users - Échec 403 Forbidden pour un non-administrateur")
    void listUsers_asNonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("GET /v1/users - Échec 401 Unauthorized sans jeton")
    void listUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("GET /v1/users/{id} - Succès de consultation d'un utilisateur")
    void getUserById_existingUser_returns200() throws Exception {
        mockMvc.perform(get("/v1/users/{id}", studentUser.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(studentUser.getId().toString())))
                .andExpect(jsonPath("$.email", is("fatou.sow@univ-zig.sn")))
                .andExpect(jsonPath("$.firstName", is("Fatou")));
    }

    @Test
    @DisplayName("GET /v1/users/{id} - Échec 404 si l'utilisateur n'existe pas")
    void getUserById_nonExistingUser_returns404() throws Exception {
        mockMvc.perform(get("/v1/users/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("PUT /v1/users/{id} - Un utilisateur peut modifier ses propres informations")
    void updateUser_byOwner_returns200() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setLastName("Sow-Ndiaye");
        request.setFirstName("Fatoumata");
        request.setPhone("779876543");
        request.setGender(UserUpdateRequest.GenderEnum.FEMALE);
        request.setBirthDate(LocalDate.of(2000, 3, 15));

        mockMvc.perform(put("/v1/users/{id}", studentUser.getId())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", is("Sow-Ndiaye")))
                .andExpect(jsonPath("$.firstName", is("Fatoumata")))
                .andExpect(jsonPath("$.phone", is("779876543")))
                .andExpect(jsonPath("$.gender", is("FEMALE")))
                .andExpect(jsonPath("$.birthDate", is("2000-03-15")));
    }

    @Test
    @DisplayName("PUT /v1/users/{id} - Échec 403 si un non-admin tente de modifier un autre compte")
    void updateUser_byOtherNonAdmin_returns403() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setLastName("TentativeHacker");

        mockMvc.perform(put("/v1/users/{id}", otherStudentUser.getId())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("PUT /v1/users/{id} - Un administrateur peut modifier le compte d'un autre utilisateur")
    void updateUser_byAdmin_returns200() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setLastName("Diop-ModifieParAdmin");

        mockMvc.perform(put("/v1/users/{id}", otherStudentUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", is("Diop-ModifieParAdmin")));
    }

    @Test
    @DisplayName("PATCH /v1/users/{id}/status - L'administrateur peut suspendre un compte")
    void updateUserStatus_byAdmin_returns200() throws Exception {
        UserStatusUpdateRequest request = new UserStatusUpdateRequest();
        request.setStatus(UserStatus.SUSPENDU);

        mockMvc.perform(patch("/v1/users/{id}/status", studentUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUSPENDU")));
    }

    @Test
    @DisplayName("PATCH /v1/users/{id}/status - Échec 403 si un non-administrateur tente de changer le statut")
    void updateUserStatus_byNonAdmin_returns403() throws Exception {
        UserStatusUpdateRequest request = new UserStatusUpdateRequest();
        request.setStatus(UserStatus.SUSPENDU);

        mockMvc.perform(patch("/v1/users/{id}/status", otherStudentUser.getId())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("DELETE /v1/users/{id} - L'administrateur peut supprimer un compte (204 No Content)")
    void deleteUser_byAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/v1/users/{id}", otherStudentUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Vérification de la suppression effective
        mockMvc.perform(get("/v1/users/{id}", otherStudentUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /v1/users/{id} - Échec 403 si un non-administrateur tente de supprimer un compte")
    void deleteUser_byNonAdmin_returns403() throws Exception {
        mockMvc.perform(delete("/v1/users/{id}", otherStudentUser.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }
}
