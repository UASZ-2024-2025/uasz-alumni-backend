package com.uasz.alumni.user.service;

import com.uasz.alumni.user.spi.model.Role;
import com.uasz.alumni.user.spi.model.RoleName;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Catalogue des rôles. Les rôles sont une énumération du contrat (pas une table) : l'id est un UUID
 * déterministe dérivé du nom, donc stable entre redémarrages et instances.
 * ATTENTION : la matrice de permissions ci-dessous est un point de départ à valider avec l'équipe.
 */
@Service
public class RoleService {

    private static final Map<RoleName, List<String>> PERMISSIONS = new EnumMap<>(RoleName.class);

    static {
        PERMISSIONS.put(RoleName.ETUDIANT, List.of(
                "profile:read", "profile:update", "cv:manage", "offers:read", "applications:create", "messages:send"));
        PERMISSIONS.put(RoleName.ALUMNI, List.of(
                "profile:read", "profile:update", "offers:read", "proposals:create", "messages:send"));
        PERMISSIONS.put(RoleName.ENSEIGNANT, List.of(
                "profile:read", "profile:update", "students:read", "alumni:read", "analytics:read", "messages:send"));
        PERMISSIONS.put(RoleName.ENTREPRISE, List.of(
                "profile:read", "profile:update", "company:manage", "offers:manage", "applications:read", "messages:send"));
        PERMISSIONS.put(RoleName.ADMINISTRATEUR, List.of(
                "users:read", "users:create", "users:update", "users:delete", "users:status", "roles:read",
                "promotions:manage", "companies:manage", "offers:moderate", "analytics:read", "messages:send"));
    }

    public List<Role> listRoles() {
        return Arrays.stream(RoleName.values())
                .map(name -> new Role()
                        .id(UUID.nameUUIDFromBytes(("alumni-uasz:role:" + name.name()).getBytes(StandardCharsets.UTF_8)))
                        .name(name)
                        .permissions(PERMISSIONS.getOrDefault(name, List.of())))
                .toList();
    }
}
