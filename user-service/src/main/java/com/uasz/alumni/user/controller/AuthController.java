package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.service.AuthService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<User> registerUser(RegisterRequest registerRequest) {
        User user = authService.registerUser(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @Override
    public ResponseEntity<AuthResponse> loginUser(LoginRequest loginRequest) {
        AuthResponse response = authService.loginUser(loginRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AuthResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        AuthResponse response = authService.refreshToken(refreshTokenRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> logoutUser(LogoutRequest logoutRequest) {
        authService.logoutUser(logoutRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MessageResponse> forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
        MessageResponse response = authService.forgotPassword(forgotPasswordRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<MessageResponse> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        MessageResponse response = authService.resetPassword(resetPasswordRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<MessageResponse> confirmEmail(ConfirmEmailRequest confirmEmailRequest) {
        MessageResponse response = authService.confirmEmail(confirmEmailRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<MessageResponse> resendConfirmation(ResendConfirmationRequest resendConfirmationRequest) {
        MessageResponse response = authService.resendConfirmation(resendConfirmationRequest);
        return ResponseEntity.ok(response);
    }
}
