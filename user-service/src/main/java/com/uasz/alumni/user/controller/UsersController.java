package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.service.UserService;
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

    private final UserService userService;

    public UsersController(UserService userService) {
        this.userService = userService;
    }

    @Override
    public ResponseEntity<UserPagedResponse> listUsers(RoleName role, UserStatus status, Integer page, Integer size) {
        UserPagedResponse response = userService.listUsers(role, status, page, size);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<User> getUserById(UUID id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @Override
    public ResponseEntity<User> updateUser(UUID id, UserUpdateRequest userUpdateRequest) {
        User updated = userService.updateUser(id, userUpdateRequest);
        return ResponseEntity.ok(updated);
    }

    @Override
    public ResponseEntity<Void> deleteUser(UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<User> updateUserStatus(UUID id, UserStatusUpdateRequest userStatusUpdateRequest) {
        User updated = userService.updateUserStatus(id, userStatusUpdateRequest);
        return ResponseEntity.ok(updated);
    }
}
