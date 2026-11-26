
package com.uasz.alumni.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.service.RoleService;
import com.uasz.alumni.user.spi.model.Role;
import com.uasz.alumni.user.spi.model.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class RolesControllerTest {

    @Mock RoleService roles;

    @Test
    void listRoles_returns200_withServiceResult() {
        List<Role> expected = List.of(new Role().id(UUID.randomUUID()).name(RoleName.ALUMNI).permissions(List.of("profile:read")));
        when(roles.listRoles()).thenReturn(expected);

        ResponseEntity<List<Role>> response = new RolesController(roles).listRoles();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(expected);
    }
}
