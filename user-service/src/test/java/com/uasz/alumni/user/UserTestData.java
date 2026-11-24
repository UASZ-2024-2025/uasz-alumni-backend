
package com.uasz.alumni.user;

import com.uasz.alumni.user.config.AppProperties;
import com.uasz.alumni.user.config.AuthProperties;
import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Fabriques et constantes communes aux tests. */
public final class UserTestData {

    public static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private UserTestData() {
    }

    public static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    public static UserAccount account(UserStatus status) {
        return account(UUID.randomUUID(), "awa.diop@uasz.sn", RoleName.ALUMNI, status);
    }

    /** Compte avec un id fixé (l'id est normalement généré par JPA au premier enregistrement). */
    public static UserAccount account(UUID id, String email, RoleName role, UserStatus status) {
        UserAccount account = new UserAccount(email, "HASH", "Awa", "Diop", role, status);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    /** max 3 échecs, verrouillage 15 min, refresh 7 j, reset 1 h. */
    public static AuthProperties authProps() {
        return new AuthProperties(Duration.ofDays(7), Duration.ofHours(1), 3, Duration.ofMinutes(15),
                new AuthProperties.BootstrapAdmin("admin@alumni-uasz.sn", "Secret123abc", "Admin", "Alumni UASZ"));
    }

    public static AppProperties appProps() {
        return new AppProperties("http://localhost:4200", "no-reply@alumni-uasz.sn", Duration.ofHours(24));
    }

    /** Pour {@code isInstanceOfSatisfying(ApiException.class, apiError(...))}. */
    public static Consumer<ApiException> apiError(HttpStatus status, String code) {
        return e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        };
    }
}
