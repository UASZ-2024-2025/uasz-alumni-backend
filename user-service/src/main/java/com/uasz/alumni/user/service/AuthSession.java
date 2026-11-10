package com.uasz.alumni.user.service;

import com.uasz.alumni.user.model.UserAccount;

/** Résultat d'une connexion ou d'un rafraîchissement ; converti en AuthResponse par le controller. */
public record AuthSession(String accessToken, String refreshToken, long expiresInSeconds, UserAccount user) {
}
