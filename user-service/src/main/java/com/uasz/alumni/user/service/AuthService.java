package com.uasz.alumni.user.service;

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

public interface AuthService {

    User registerUser(RegisterRequest request);

    AuthResponse loginUser(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logoutUser(LogoutRequest request);

    MessageResponse forgotPassword(ForgotPasswordRequest request);

    MessageResponse resetPassword(ResetPasswordRequest request);

    MessageResponse confirmEmail(ConfirmEmailRequest request);

    MessageResponse resendConfirmation(ResendConfirmationRequest request);
}

