package com.uasz.alumni.user.utils;

import com.uasz.alumni.user.model.RoleEntity;
import com.uasz.alumni.user.model.UserEntity;
import com.uasz.alumni.user.spi.model.Role;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserPagedResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;

public final class UserMapper {

    private UserMapper() {}

    public static User toUser(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        User user = new User();
        user.setId(entity.getId());
        user.setLastName(entity.getLastName());
        user.setFirstName(entity.getFirstName());
        user.setEmail(entity.getEmail());
        user.setRole(entity.getRoleName());
        user.setStatus(entity.getStatus());
        user.setCreatedAt(entity.getCreatedAt());
        user.setPhone(entity.getPhone());
        if (entity.getGender() != null) {
            try {
                user.setGender(User.GenderEnum.fromValue(entity.getGender()));
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid gender value
            }
        }
        user.setBirthDate(entity.getBirthDate());
        return user;
    }

    public static UserPagedResponse toUserPagedResponse(Page<UserEntity> page) {
        UserPagedResponse response = new UserPagedResponse();
        List<User> content = page.getContent().stream()
                .map(UserMapper::toUser)
                .toList();
        response.setContent(content);
        response.setTotalElements((int) page.getTotalElements());
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        return response;
    }

    public static Role toRole(RoleEntity entity) {
        if (entity == null) {
            return null;
        }
        Role role = new Role();
        role.setName(entity.getName());
        role.setPermissions(new ArrayList<>(entity.getPermissions()));
        return role;
    }
}

