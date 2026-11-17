package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.service.UserService;
import com.uasz.alumni.user.spi.api.UsersApi;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserCreateRequest;
import com.uasz.alumni.user.spi.model.UserPagedResponse;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserStatusUpdateRequest;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import com.uasz.alumni.user.utils.UserMapper;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController implements UsersApi {

    private final UserService users;

    public UsersController(UserService users) {
        this.users = users;
    }

    @Override
    public ResponseEntity<UserPagedResponse> listUsers(RoleName role, UserStatus status, Integer page, Integer size) {
        Page<UserAccount> result = users.list(role, status, page, size);
        return ResponseEntity.ok(new UserPagedResponse()
                .content(result.getContent().stream().map(UserMapper::toApi).toList())
                .totalElements((int) result.getTotalElements())
                .page(result.getNumber())
                .size(result.getSize()));
    }

    @Override
    public ResponseEntity<User> createUser(UserCreateRequest userCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserMapper.toApi(users.create(userCreateRequest)));
    }

    @Override
    public ResponseEntity<User> getUserById(UUID id) {
        return ResponseEntity.ok(UserMapper.toApi(users.get(id)));
    }

    @Override
    public ResponseEntity<User> updateUser(UUID id, UserUpdateRequest userUpdateRequest) {
        return ResponseEntity.ok(UserMapper.toApi(users.update(id, userUpdateRequest)));
    }

    @Override
    public ResponseEntity<Void> deleteUser(UUID id) {
        users.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<User> updateUserStatus(UUID id, UserStatusUpdateRequest userStatusUpdateRequest) {
        return ResponseEntity.ok(UserMapper.toApi(users.updateStatus(id, userStatusUpdateRequest.getStatus())));
    }
}
