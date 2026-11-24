package com.uasz.alumni.alumnistudentcompany.security;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Appelant courant, tel que posé par JwtAuthenticationFilter (principal AuthenticatedUser). */
@Component
public class CurrentUserProvider {

    public AuthenticatedUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise");
    }
}
