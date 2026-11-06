package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.exceptions.NotImplementedYetException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    @Override
    public ResponseEntity<User> registerUser(RegisterRequest registerRequest) {
        // TODO : créer le compte (statut EN_ATTENTE) + envoyer l'email de confirmation -> 201
        throw new NotImplementedYetException("registerUser");
    }

    @Override
    public ResponseEntity<AuthResponse> loginUser(LoginRequest loginRequest) {
        // TODO : vérifier identifiants/statut, émettre accessToken + refreshToken -> 200 / 401 / 403
        throw new NotImplementedYetException("loginUser");
    }

    @Override
    public ResponseEntity<AuthResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        // TODO : valider le refresh token, émettre un nouveau jeton -> 200 / 401
        throw new NotImplementedYetException("refreshToken");
    }

    @Override
    public ResponseEntity<Void> logoutUser(LogoutRequest logoutRequest) {
        // TODO : invalider le refresh token -> 204
        throw new NotImplementedYetException("logoutUser");
    }

    @Override
    public ResponseEntity<MessageResponse> forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
        // TODO : réponse générique (anti-énumération) + email de réinitialisation -> 200
        throw new NotImplementedYetException("forgotPassword");
    }

    @Override
    public ResponseEntity<MessageResponse> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        // TODO : valider le token, appliquer la politique de mot de passe -> 200 / 400 / 422
        throw new NotImplementedYetException("resetPassword");
    }

    @Override
    public ResponseEntity<MessageResponse> confirmEmail(ConfirmEmailRequest confirmEmailRequest) {
        // TODO : valider le token, passer le compte à ACTIF -> 200 / 400
        throw new NotImplementedYetException("confirmEmail");
    }

    @Override
    public ResponseEntity<MessageResponse> resendConfirmation(ResendConfirmationRequest resendConfirmationRequest) {
        // TODO : renvoyer l'email de confirmation -> 200 / 409 (déjà activé)
        throw new NotImplementedYetException("resendConfirmation");
    }
}
