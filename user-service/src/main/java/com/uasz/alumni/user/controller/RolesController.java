package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.service.RoleService;
import com.uasz.alumni.user.spi.api.RolesApi;
import com.uasz.alumni.user.spi.model.Role;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RolesController implements RolesApi {

    private final RoleService roles;

    public RolesController(RoleService roles) {
        this.roles = roles;
    }

    @Override
    public ResponseEntity<List<Role>> listRoles() {
        return ResponseEntity.ok(roles.listRoles());
    }
}
