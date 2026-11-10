package com.uasz.alumni.user.utils;

import java.nio.charset.StandardCharsets;

/** 8 à 72 octets (limite de BCrypt), au moins une lettre et un chiffre. */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        return password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
    }
}
