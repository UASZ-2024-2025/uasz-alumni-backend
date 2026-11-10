
package com.uasz.alumni.user.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"motdepasse1", "Abcdefg1", "1234567a", "é-mot-de-passe-2"})
    void acceptsPasswordsWithLetterAndDigit(String password) {
        assertThat(PasswordPolicy.isValid(password)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc1", "abcdefgh", "12345678", "        1", "!!!!!!!!!!"})
    void rejectsTooShortOrMissingLetterOrDigit(String password) {
        assertThat(PasswordPolicy.isValid(password)).isFalse();
    }

    @Test
    void accepts72Bytes_rejects73Bytes() {
        assertThat(PasswordPolicy.isValid("a1" + "x".repeat(70))).isTrue();
        assertThat(PasswordPolicy.isValid("a1" + "x".repeat(71))).isFalse();
    }

    @Test
    void limitIsInBytes_notCharacters() {
        // 37 × "é" (2 octets) + "1" = 38 caractères mais 75 octets : refusé (BCrypt tronque à 72 octets)
        assertThat(PasswordPolicy.isValid("é".repeat(37) + "1")).isFalse();
    }
}
