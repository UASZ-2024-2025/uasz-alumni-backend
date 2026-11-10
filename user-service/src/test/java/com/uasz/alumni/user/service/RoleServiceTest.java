
package com.uasz.alumni.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.uasz.alumni.user.spi.model.Role;
import com.uasz.alumni.user.spi.model.RoleName;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleServiceTest {

    private final RoleService service = new RoleService();

    @Test
    void listRoles_returnsAllFiveRoles() {
        List<Role> roles = service.listRoles();

        assertThat(roles).extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.ALUMNI, RoleName.ETUDIANT, RoleName.ENSEIGNANT,
                        RoleName.ENTREPRISE, RoleName.ADMINISTRATEUR);
    }

    @Test
    void listRoles_idsAreStableAndUnique() {
        List<Role> first = service.listRoles();
        List<Role> second = new RoleService().listRoles();

        assertThat(first).extracting(Role::getId).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(first).extracting(Role::getId).containsExactlyElementsOf(second.stream().map(Role::getId).toList());
    }

    @Test
    void everyRoleHasPermissions() {
        assertThat(service.listRoles()).allSatisfy(role -> assertThat(role.getPermissions()).isNotEmpty());
    }

    @Test
    void onlyAdministrateurCanManageUsers() {
        for (Role role : service.listRoles()) {
            if (role.getName() == RoleName.ADMINISTRATEUR) {
                assertThat(role.getPermissions()).contains("users:read", "users:create", "users:delete", "users:status");
            } else {
                assertThat(role.getPermissions()).noneMatch(p -> p.startsWith("users:"));
            }
        }
    }

    @Test
    void entrepriseCanManageOffers_butNotStudentsOrAlumniData() {
        Role entreprise = service.listRoles().stream()
                .filter(r -> r.getName() == RoleName.ENTREPRISE).findFirst().orElseThrow();

        assertThat(entreprise.getPermissions()).contains("offers:manage", "company:manage")
                .doesNotContain("students:read", "alumni:read");
    }
}
