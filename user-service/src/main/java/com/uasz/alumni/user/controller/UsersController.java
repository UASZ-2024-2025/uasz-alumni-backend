package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.exceptions.NotImplementedYetException;
import com.uasz.alumni.user.spi.api.UsersApi;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserPagedResponse;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserStatusUpdateRequest;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController implements UsersApi {

    @Override
    public ResponseEntity<UserPagedResponse> listUsers(RoleName role, UserStatus status, Integer page, Integer size) {
        // TODO : liste paginée filtrée par rôle/statut (ADMINISTRATEUR)
        throw new NotImplementedYetException("listUsers");
    }

    @Override
    public ResponseEntity<User> getUserById(UUID id) {
        // TODO : récupérer l'utilisateur -> 200 / 404
        throw new NotImplementedYetException("getUserById");
    }

    @Override
    public ResponseEntity<User> updateUser(UUID id, UserUpdateRequest userUpdateRequest) {
        // TODO : mise à jour (soi-même ou ADMINISTRATEUR) -> 200 / 400 / 403 / 404
        throw new NotImplementedYetException("updateUser");
    }

    @Override
    public ResponseEntity<Void> deleteUser(UUID id) {
        // TODO : suppression -> 204 / 403 / 404
        throw new NotImplementedYetException("deleteUser");
    }

    @Override
    public ResponseEntity<User> updateUserStatus(UUID id, UserStatusUpdateRequest userStatusUpdateRequest) {
        // TODO : activer/désactiver/suspendre (ADMINISTRATEUR uniquement) -> 200 / 403 / 404
        throw new NotImplementedYetException("updateUserStatus");
    }
}
