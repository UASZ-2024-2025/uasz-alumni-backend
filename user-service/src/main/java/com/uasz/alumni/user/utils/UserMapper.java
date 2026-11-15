package com.uasz.alumni.user.utils;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.spi.model.User;
import java.time.ZoneOffset;

/** Conversion entité -> modèle d'API. Le hash du mot de passe n'est jamais exposé. */
public final class UserMapper {

    private UserMapper() {
    }

    public static User toApi(UserAccount account) {
        return new User()
                .id(account.getId())
                .email(account.getEmail())
                .firstName(account.getFirstName())
                .lastName(account.getLastName())
                .role(account.getRole())
                .status(account.getStatus())
                .phone(account.getPhone())
                .gender(account.getGender())
                .birthDate(account.getBirthDate())
                .createdAt(account.getCreatedAt() == null ? null : account.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
