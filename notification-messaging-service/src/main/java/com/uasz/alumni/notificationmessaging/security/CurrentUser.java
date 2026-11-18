package com.uasz.alumni.notificationmessaging.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

public final class CurrentUser {

    private CurrentUser() {}

    /** Id de l'utilisateur connecté, lu dans l'objet AuthenticatedUser posé par le filtre JWT. */
    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}