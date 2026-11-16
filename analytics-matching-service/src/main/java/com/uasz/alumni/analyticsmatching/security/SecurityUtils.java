package com.uasz.alumni.analyticsmatching.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UserPrincipal> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static UUID getCurrentUserId() {
        return getCurrentUser().map(UserPrincipal::getId).orElse(null);
    }

    public static boolean isCurrentUserAdmin() {
        return getCurrentUser()
                .map(u -> "ADMINISTRATEUR".equalsIgnoreCase(u.getRole()))
                .orElse(false);
    }

    public static boolean isCurrentUserId(UUID id) {
        if (id == null) return false;
        return getCurrentUser()
                .map(u -> id.equals(u.getId()))
                .orElse(false);
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String && "anonymousUser".equals(authentication.getPrincipal()));
    }
}

