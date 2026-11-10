
package com.uasz.alumni.user.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class TextUtilsTest {

    @Test
    void normalizeEmail_trimsAndLowercases() {
        assertThat(TextUtils.normalizeEmail("  Awa.Diop@UASZ.sn \n")).isEqualTo("awa.diop@uasz.sn");
    }

    @Test
    void normalizeEmail_doesNotDependOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); // en turc, "I".toLowerCase() donne "ı"
            assertThat(TextUtils.normalizeEmail("TITLE@UASZ.SN")).isEqualTo("title@uasz.sn");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
