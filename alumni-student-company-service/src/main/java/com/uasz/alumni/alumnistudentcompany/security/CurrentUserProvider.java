package com.uasz.alumni.alumnistudentcompany.security;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Appelant courant, tel que posé par JwtAuthenticationFilter (principal AuthenticatedUser). */
@Component
public class CurrentUserProvider {

    private static final String BEARER_PREFIX = "Bearer ";

    public AuthenticatedUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise");
    }

    /**
     * Jeton brut de la requête en cours (sans « Bearer »), pour le propager à un autre service (ADR-0006).
     * Appelé après le filtre JWT : le jeton est donc présent et valide.
     */
    public String requireBearerToken() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String header = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                return header.substring(BEARER_PREFIX.length()).trim();
            }
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise");
    }
}
