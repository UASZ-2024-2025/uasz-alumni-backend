package com.uasz.alumni.notificationmessaging.security;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Identifiant (claim {@code sub}) de l'utilisateur authentifié. */
@Component
public class CurrentUserProvider {

    public UUID requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Jeton invalide : sub n'est pas un UUID");
        }
    }
}
