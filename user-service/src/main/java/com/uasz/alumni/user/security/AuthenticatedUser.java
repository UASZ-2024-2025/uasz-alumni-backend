package com.uasz.alumni.user.security;

import com.uasz.alumni.user.spi.model.RoleName;
import java.util.UUID;

/** Principal Spring Security : léger, sans entité JPA. */
public record AuthenticatedUser(UUID id, String email, RoleName role) {
}
