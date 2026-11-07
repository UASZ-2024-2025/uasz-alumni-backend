package com.uasz.alumni.alumnistudentcompany.security;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    private static final String ROLE_ADMIN = "ROLE_ADMINISTRATEUR";

    public CurrentUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise");
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Jeton invalide : sub n'est pas un UUID");
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
        return new CurrentUser(userId, admin);
    }
}