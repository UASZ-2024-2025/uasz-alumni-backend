package com.uasz.alumni.user.security;

import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.ForbiddenException;
import com.uasz.alumni.user.spi.model.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Accès à l'utilisateur authentifié de la requête courante. */
@Component
public class CurrentUser {

    public Optional<AuthenticatedUser> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public AuthenticatedUser require() {
        return principal().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentification requise."));
    }

    public boolean isAdmin() {
        return principal().map(u -> u.role() == RoleName.ADMINISTRATEUR).orElse(false);
    }

    /** Autorise l'utilisateur lui-même ou un ADMINISTRATEUR, sinon 403. */
    public void requireSelfOrAdmin(UUID targetId) {
        AuthenticatedUser me = require();
        if (!me.id().equals(targetId) && me.role() != RoleName.ADMINISTRATEUR) {
            throw new ForbiddenException();
        }
    }
}
