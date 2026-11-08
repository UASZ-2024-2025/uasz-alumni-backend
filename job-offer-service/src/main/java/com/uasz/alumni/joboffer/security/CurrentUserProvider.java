package com.uasz.alumni.joboffer.security;

import com.uasz.alumni.joboffer.exceptions.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Lit l'utilisateur courant depuis le JWT validé par Spring Security. */
@Component
public class CurrentUserProvider {

    public CurrentUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise");
        }
        try {
            return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Jeton invalide : sub n'est pas un UUID");
        }
    }
}
