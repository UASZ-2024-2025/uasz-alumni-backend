
package com.uasz.alumni.user.controller;

import static com.uasz.alumni.user.UserTestData.account;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.service.UserService;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserCreateRequest;
import com.uasz.alumni.user.spi.model.UserPagedResponse;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserStatusUpdateRequest;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UsersControllerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock UserService users;

    private UsersController controller;

    @BeforeEach
    void setUp() {
        controller = new UsersController(users);
    }

    private static UserAccount user(RoleName role, UserStatus status) {
        return account(ID, "awa.diop@uasz.sn", role, status);
    }

    @Test
    void listUsers_mapsPageMetadataAndContent() {
        UserAccount a = account(UUID.randomUUID(), "a@uasz.sn", RoleName.ALUMNI, UserStatus.ACTIF);
        UserAccount b = account(UUID.randomUUID(), "b@uasz.sn", RoleName.ETUDIANT, UserStatus.ACTIF);
        when(users.list(RoleName.ALUMNI, UserStatus.ACTIF, 1, 2))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(1, 2), 5));

        ResponseEntity<UserPagedResponse> response = controller.listUsers(RoleName.ALUMNI, UserStatus.ACTIF, 1, 2);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        UserPagedResponse body = response.getBody();
        assertThat(body.getContent()).extracting(User::getEmail).containsExactly("a@uasz.sn", "b@uasz.sn");
        assertThat(body.getTotalElements()).isEqualTo(5);
        assertThat(body.getPage()).isEqualTo(1);
        assertThat(body.getSize()).isEqualTo(2);
    }

    @Test
    void createUser_returns201() {
        UserCreateRequest request = new UserCreateRequest().lastName("Diop").firstName("Awa")
                .email("rh@gainde.sn").role(RoleName.ENTREPRISE);
        when(users.create(request)).thenReturn(user(RoleName.ENTREPRISE, UserStatus.EN_ATTENTE));

        ResponseEntity<User> response = controller.createUser(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getRole()).isEqualTo(RoleName.ENTREPRISE);
        assertThat(response.getBody().getStatus()).isEqualTo(UserStatus.EN_ATTENTE);
    }

    @Test
    void getUserById_returns200() {
        when(users.get(ID)).thenReturn(user(RoleName.ALUMNI, UserStatus.ACTIF));

        ResponseEntity<User> response = controller.getUserById(ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getId()).isEqualTo(ID);
    }

    @Test
    void updateUser_returnsUpdatedUser() {
        UserUpdateRequest request = new UserUpdateRequest().firstName("Aïcha");
        when(users.update(ID, request)).thenReturn(user(RoleName.ALUMNI, UserStatus.ACTIF));

        ResponseEntity<User> response = controller.updateUser(ID, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(users).update(ID, request);
    }

    @Test
    void deleteUser_returns204() {
        ResponseEntity<Void> response = controller.deleteUser(ID);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(users).delete(ID);
    }

    @Test
    void updateUserStatus_delegatesWithRequestedStatus() {
        when(users.updateStatus(ID, UserStatus.SUSPENDU)).thenReturn(user(RoleName.ALUMNI, UserStatus.SUSPENDU));

        ResponseEntity<User> response = controller.updateUserStatus(ID, new UserStatusUpdateRequest().status(UserStatus.SUSPENDU));

        assertThat(response.getBody().getStatus()).isEqualTo(UserStatus.SUSPENDU);
    }
}
