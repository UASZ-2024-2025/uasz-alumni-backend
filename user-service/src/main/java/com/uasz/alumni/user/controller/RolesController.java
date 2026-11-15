package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.repository.RoleRepository;
import com.uasz.alumni.user.spi.api.RolesApi;
import com.uasz.alumni.user.spi.model.Role;
import com.uasz.alumni.user.utils.UserMapper;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RolesController implements RolesApi {

    private final RoleRepository roleRepository;

    public RolesController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public ResponseEntity<List<Role>> listRoles() {
        List<Role> roles = roleRepository.findAll().stream()
                .map(UserMapper::toRole)
                .toList();
        return ResponseEntity.ok(roles);
    }
}
