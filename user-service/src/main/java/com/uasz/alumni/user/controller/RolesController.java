package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.exceptions.NotImplementedYetException;
import com.uasz.alumni.user.spi.api.RolesApi;
import com.uasz.alumni.user.spi.model.Role;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RolesController implements RolesApi {

    @Override
    public ResponseEntity<List<Role>> listRoles() {
        // TODO : lister les rôles et leurs permissions
        throw new NotImplementedYetException("listRoles");
    }
}
