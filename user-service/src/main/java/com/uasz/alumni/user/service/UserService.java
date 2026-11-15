package com.uasz.alumni.user.service;

import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserPagedResponse;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserStatusUpdateRequest;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import java.util.UUID;

public interface UserService {

    UserPagedResponse listUsers(RoleName role, UserStatus status, Integer page, Integer size);

    User getUserById(UUID id);

    User updateUser(UUID id, UserUpdateRequest request);

    void deleteUser(UUID id);

    User updateUserStatus(UUID id, UserStatusUpdateRequest request);
}

