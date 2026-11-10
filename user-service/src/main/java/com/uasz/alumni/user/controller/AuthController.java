package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.security.CurrentUser;
import com.uasz.alumni.user.service.AuthService;
import com.uasz.alumni.user.service.AuthSession;
import com.uasz.alumni.user.service.PasswordService;
import com.uasz.alumni.user.service.RegistrationService;
import com.uasz.alumni.user.spi.api.AuthApi;
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
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.utils.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final RegistrationService registration;
    private final AuthService auth;
    private final PasswordService passwords;
    private final CurrentUser currentUser;

    public AuthController(RegistrationService registration, AuthService auth,
                          PasswordService passwords, CurrentUser currentUser) {
        this.registration = registration;
        this.auth = auth;
        this.passwords = passwords;
        this.currentUser = currentUser;
    }

    // ================= AL-18 : INSCRIPTION =================

    @Override
    public ResponseEntity<User> registerUser(RegisterRequest registerRequest) {
        UserAccount account = registration.register(registerRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserMapper.toApi(account));
    }

    @Override
    public ResponseEntity<MessageResponse> confirmEmail(
            ConfirmEmailRequest confirmEmailRequest) {

        registration.confirmEmail(confirmEmailRequest.getToken());

        return ResponseEntity.ok(
                new MessageResponse().message("Compte activé avec succès.")
        );
    }

    @Override
    public ResponseEntity<MessageResponse> resendConfirmation(
            ResendConfirmationRequest resendConfirmationRequest) {

        registration.resendConfirmation(resendConfirmationRequest.getEmail());

        return ResponseEntity.ok(
                new MessageResponse().message(
                        "Si un compte en attente existe pour cette adresse, "
                        + "un email de confirmation vient d'être envoyé."
                )
        );
    }

    // ================= AL-19 : AUTHENTIFICATION =================

    @Override
    public ResponseEntity<AuthResponse> loginUser(LoginRequest loginRequest) {
        return ResponseEntity.ok(toResponse(auth.login(loginRequest.getEmail(), loginRequest.getPassword())));
    }

    @Override
    public ResponseEntity<AuthResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        return ResponseEntity.ok(toResponse(auth.refresh(refreshTokenRequest.getRefreshToken())));
    }

    @Override
    public ResponseEntity<Void> logoutUser(LogoutRequest logoutRequest) {
        auth.logout(currentUser.require().id(), logoutRequest.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    // ================= AL-20 : MOT DE PASSE =================

    @Override
    public ResponseEntity<MessageResponse> forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
        passwords.forgotPassword(forgotPasswordRequest.getEmail());

        return ResponseEntity.ok(new MessageResponse().message(
                "Si un compte correspond à cette adresse, un email de réinitialisation vient d'être envoyé."));
    }

    @Override
    public ResponseEntity<MessageResponse> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        passwords.resetPassword(resetPasswordRequest.getToken(), resetPasswordRequest.getNewPassword());

        return ResponseEntity.ok(new MessageResponse().message("Mot de passe réinitialisé avec succès."));
    }

    @Override
    public ResponseEntity<MessageResponse> changePassword(ChangePasswordRequest changePasswordRequest) {
        passwords.changePassword(currentUser.require().id(),
                changePasswordRequest.getCurrentPassword(), changePasswordRequest.getNewPassword());

        return ResponseEntity.ok(new MessageResponse().message(
                "Mot de passe modifié. Veuillez vous reconnecter."));
    }

    private static AuthResponse toResponse(AuthSession session) {
        return new AuthResponse()
                .accessToken(session.accessToken())
                .refreshToken(session.refreshToken())
                .expiresIn((int) session.expiresInSeconds())
                .user(UserMapper.toApi(session.user()));
    }
}
