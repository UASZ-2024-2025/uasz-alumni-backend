package com.uasz.alumni.user.utils;

import java.util.Locale;

public final class TextUtils {

    private TextUtils() {
    }

    /** trim + minuscules : les emails sont toujours stockés normalisés. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
