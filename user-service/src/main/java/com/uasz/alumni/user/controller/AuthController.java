package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.exceptions.NotImplementedYetException;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.service.RegistrationService;
import com.uasz.alumni.user.spi.api.AuthApi;
import com.uasz.alumni.user.spi.model.AuthResponse;
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

/**
 * Implémentation de l'interface {@link AuthApi} générée depuis le contrat.
 *
 * <ul>
 *   <li>AL-18 : {@code registerUser}, {@code confirmEmail}, {@code resendConfirmation}</li>
 *   <li>AL-19 : {@code loginUser}, {@code refreshToken}, {@code logoutUser} (à implémenter)</li>
 *   <li>AL-20 : {@code forgotPassword}, {@code resetPassword} (à implémenter)</li>
 * </ul>
 * Les méthodes des autres tickets répondent 501 en attendant : remplacez simplement leur corps.
 */
@RestController
public class AuthController implements AuthApi {

    private final RegistrationService registration;

    public AuthController(RegistrationService registration) {
        this.registration = registration;
    }

    // ───── AL-18 ─────

    @Override
    public ResponseEntity<User> registerUser(RegisterRequest registerRequest) {
        UserAccount account = registration.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserMapper.toApi(account));
    }

    @Override
    public ResponseEntity<MessageResponse> confirmEmail(ConfirmEmailRequest confirmEmailRequest) {
        registration.confirmEmail(confirmEmailRequest.getToken());
        return ResponseEntity.ok(new MessageResponse().message("Compte activé avec succès."));
    }

    @Override
    public ResponseEntity<MessageResponse> resendConfirmation(ResendConfirmationRequest resendConfirmationRequest) {
        registration.resendConfirmation(resendConfirmationRequest.getEmail());
        return ResponseEntity.ok(new MessageResponse()
                .message("Si un compte en attente existe pour cette adresse, un email de confirmation vient d'être envoyé."));
    }

    // ───── AL-19 (connexion) : à implémenter ─────

    @Override
    public ResponseEntity<AuthResponse> loginUser(LoginRequest loginRequest) {
        throw new NotImplementedYetException("AL-19");
    }

    @Override
    public ResponseEntity<AuthResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        throw new NotImplementedYetException("AL-19");
    }

    @Override
    public ResponseEntity<Void> logoutUser(LogoutRequest logoutRequest) {
        throw new NotImplementedYetException("AL-19");
    }

    // ───── AL-20 (mot de passe) : à implémenter ─────

    @Override
    public ResponseEntity<MessageResponse> forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
        throw new NotImplementedYetException("AL-20");
    }

    @Override
    public ResponseEntity<MessageResponse> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        throw new NotImplementedYetException("AL-20");
    }
}
