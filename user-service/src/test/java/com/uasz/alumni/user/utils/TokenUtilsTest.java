package com.uasz.alumni.user.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenUtilsTest {

    @Test
    void generateToken_isUrlSafeUniqueAnd256Bits() {
        String a = TokenUtils.generateToken();
        String b = TokenUtils.generateToken();

        assertThat(a).isNotEqualTo(b).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void sha256Hex_matchesKnownVector() {
        assertThat(TokenUtils.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
