
package com.uasz.alumni.user.controller;

import static com.uasz.alumni.user.UserTestData.account;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.security.AuthenticatedUser;
import com.uasz.alumni.user.security.CurrentUser;
import com.uasz.alumni.user.service.AuthService;
import com.uasz.alumni.user.service.AuthSession;
import com.uasz.alumni.user.service.PasswordService;
import com.uasz.alumni.user.service.RegistrationService;
import com.uasz.alumni.user.spi.model.AuthResponse;
import com.uasz.alumni.user.spi.model.ChangePasswordRequest;
import com.uasz.alumni.user.spi.model.ConfirmEmailRequest;
import com.uasz.alumni.user.spi.model.ForgotPasswordRequest;
import com.uasz.alumni.user.spi.model.LoginRequest;
import com.uasz.alumni.user.spi.model.LogoutRequest;
import com.uasz.alumni.user.spi.model.MessageResponse;
import com.uasz.alumni.user.spi.model.RefreshTokenRequest;
import com.uasz.alumni.user.spi.model.RegisterRequest;
import com.uasz.alumni.user.spi.model.ResendConfirmationRequest;
import com.uasz.alumni.user.spi.model.ResetPasswordRequest;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock RegistrationService registration;
    @Mock AuthService auth;
    @Mock PasswordService passwords;
    @Mock CurrentUser currentUser;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(registration, auth, passwords, currentUser);
    }

    private static UserAccount user() {
        return account(ID, "awa.diop@uasz.sn", RoleName.ALUMNI, UserStatus.ACTIF);
    }

    @Test
    void registerUser_returns201_withMappedUser() {
        RegisterRequest request = new RegisterRequest("Diop", "Awa", "awa.diop@uasz.sn", "motdepasse1", RoleName.ALUMNI);
        when(registration.register(request)).thenReturn(user());

        ResponseEntity<User> response = controller.registerUser(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getId()).isEqualTo(ID);
        assertThat(response.getBody().getEmail()).isEqualTo("awa.diop@uasz.sn");
    }

    @Test
    void confirmEmail_returns200_withMessage() {
        ResponseEntity<MessageResponse> response = controller.confirmEmail(new ConfirmEmailRequest().token("tok"));

        verify(registration).confirmEmail("tok");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getMessage()).isEqualTo("Compte activé avec succès.");
    }

    @Test
    void resendConfirmation_returnsGenericMessage() {
        ResponseEntity<MessageResponse> response =
                controller.resendConfirmation(new ResendConfirmationRequest().email("awa.diop@uasz.sn"));

        verify(registration).resendConfirmation("awa.diop@uasz.sn");
        assertThat(response.getBody().getMessage()).contains("Si un compte en attente existe");
    }

    @Test
    void loginUser_mapsSessionToAuthResponse() {
        when(auth.login("awa.diop@uasz.sn", "motdepasse1")).thenReturn(new AuthSession("ACCESS", "REFRESH", 900L, user()));

        ResponseEntity<AuthResponse> response =
                controller.loginUser(new LoginRequest().email("awa.diop@uasz.sn").password("motdepasse1"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        AuthResponse body = response.getBody();
        assertThat(body.getAccessToken()).isEqualTo("ACCESS");
        assertThat(body.getRefreshToken()).isEqualTo("REFRESH");
        assertThat(body.getExpiresIn()).isEqualTo(900);
        assertThat(body.getUser().getId()).isEqualTo(ID);
        assertThat(body.getUser().getRole()).isEqualTo(RoleName.ALUMNI);
    }

    @Test
    void refreshToken_mapsSessionToAuthResponse() {
        when(auth.refresh("old")).thenReturn(new AuthSession("ACCESS2", "NEW", 900L, user()));

        AuthResponse body = controller.refreshToken(new RefreshTokenRequest().refreshToken("old")).getBody();

        assertThat(body.getAccessToken()).isEqualTo("ACCESS2");
        assertThat(body.getRefreshToken()).isEqualTo("NEW");
    }

    @Test
    void logoutUser_returns204_andRevokesTokenOfAuthenticatedUser() {
        when(currentUser.require()).thenReturn(new AuthenticatedUser(ID, "awa.diop@uasz.sn", RoleName.ALUMNI));

        ResponseEntity<Void> response = controller.logoutUser(new LogoutRequest().refreshToken("raw"));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(auth).logout(ID, "raw");
    }

    @Test
    void forgotPassword_alwaysReturnsTheSameGenericMessage() {
        ResponseEntity<MessageResponse> response =
                controller.forgotPassword(new ForgotPasswordRequest().email("inconnu@uasz.sn"));

        verify(passwords).forgotPassword("inconnu@uasz.sn");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getMessage()).contains("Si un compte correspond");
    }

    @Test
    void resetPassword_delegatesAndConfirms() {
        ResponseEntity<MessageResponse> response =
                controller.resetPassword(new ResetPasswordRequest().token("tok").newPassword("Nouveau123"));

        verify(passwords).resetPassword("tok", "Nouveau123");
        assertThat(response.getBody().getMessage()).isEqualTo("Mot de passe réinitialisé avec succès.");
    }

    @Test
    void changePassword_usesAuthenticatedUserId_andAsksToReconnect() {
        when(currentUser.require()).thenReturn(new AuthenticatedUser(ID, "awa.diop@uasz.sn", RoleName.ALUMNI));

        ResponseEntity<MessageResponse> response = controller.changePassword(
                new ChangePasswordRequest().currentPassword("Actuel123").newPassword("Nouveau123"));

        verify(passwords).changePassword(ID, "Actuel123", "Nouveau123");
        assertThat(response.getBody().getMessage()).contains("reconnecter");
    }
}
