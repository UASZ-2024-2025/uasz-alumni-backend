
package com.uasz.alumni.user.jwt;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.account;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.user.config.JwtProperties;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = secret(64, 1);
    private static final String ISSUER = "alumni-uasz";
    private static final Duration TTL = Duration.ofMinutes(15);

    private static String secret(int bytes, int seed) {
        byte[] b = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            b[i] = (byte) (i * 7 + seed);
        }
        return Base64.getEncoder().encodeToString(b);
    }

    private static JwtService service(String secret, String issuer, Duration ttl, Instant now) {
        return new JwtService(new JwtProperties(secret, issuer, ttl), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static JwtService service() {
        return service(SECRET, ISSUER, TTL, NOW);
    }

    private static UserAccount user(UUID id) {
        return account(id, "awa.diop@uasz.sn", RoleName.ALUMNI, UserStatus.ACTIF);
    }

    // ───── émission / validation ─────

    @Test
    void generatedToken_isParsedBackToUserId() {
        UUID id = UUID.randomUUID();

        String token = service().generateAccessToken(user(id));

        assertThat(service().parseUserId(token)).contains(id);
    }

    @Test
    void token_carriesRoleEmailIssuerAndSubject() {
        UUID id = UUID.randomUUID();

        String token = service().generateAccessToken(user(id));

        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload)
                .contains("\"sub\":\"" + id + "\"")
                .contains("\"role\":\"ALUMNI\"")
                .contains("\"email\":\"awa.diop@uasz.sn\"")
                .contains("\"iss\":\"alumni-uasz\"")
                .contains("\"jti\"");
    }

    @Test
    void twoTokensForSameUser_differ() {
        UserAccount user = user(UUID.randomUUID());

        assertThat(service().generateAccessToken(user)).isNotEqualTo(service().generateAccessToken(user));
    }

    @Test
    void accessTtlSeconds_matchesConfiguration() {
        assertThat(service().accessTtlSeconds()).isEqualTo(900L);
    }

    // ───── rejets ─────

    @Test
    void expiredToken_isRejected_validOneIsAccepted() {
        UUID id = UUID.randomUUID();
        String token = service().generateAccessToken(user(id));

        assertThat(service(SECRET, ISSUER, TTL, NOW.plus(Duration.ofMinutes(14))).parseUserId(token)).contains(id);
        assertThat(service(SECRET, ISSUER, TTL, NOW.plus(Duration.ofMinutes(16))).parseUserId(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        String token = service(secret(64, 99), ISSUER, TTL, NOW).generateAccessToken(user(UUID.randomUUID()));

        assertThat(service().parseUserId(token)).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuer_isRejected() {
        String token = service(SECRET, "autre-emetteur", TTL, NOW).generateAccessToken(user(UUID.randomUUID()));

        assertThat(service().parseUserId(token)).isEmpty();
    }

    @Test
    void tamperedPayload_isRejected() {
        String token = service().generateAccessToken(user(UUID.randomUUID()));
        String[] parts = token.split("\\.");
        String payload = parts[1];
        String tampered = payload.substring(0, 5) + (payload.charAt(5) == 'A' ? 'B' : 'A') + payload.substring(6);

        assertThat(service().parseUserId(parts[0] + "." + tampered + "." + parts[2])).isEmpty();
    }

    @Test
    void garbageTokens_areRejectedWithoutException() {
        assertThat(service().parseUserId("")).isEmpty();
        assertThat(service().parseUserId("not-a-jwt")).isEmpty();
        assertThat(service().parseUserId("a.b.c")).isEmpty();
    }

    // ───── validation de la configuration au démarrage ─────

    @Test
    void constructor_acceptsMinimum32ByteSecret() {
        assertThat(service(secret(32, 3), ISSUER, TTL, NOW)).isNotNull();
    }

    @Test
    void constructor_rejectsMissingSecret() {
        assertThatThrownBy(() -> service(null, ISSUER, TTL, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> service("  ", ISSUER, TTL, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
    }

    @Test
    void constructor_rejectsNonBase64Secret() {
        assertThatThrownBy(() -> service("%%%pas-du-base64%%%", ISSUER, TTL, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }

    @Test
    void constructor_rejectsTooShortSecret() {
        assertThatThrownBy(() -> service(secret(16, 1), ISSUER, TTL, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 octets");
    }

    @Test
    void constructor_rejectsMissingIssuer() {
        assertThatThrownBy(() -> service(SECRET, " ", TTL, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_ISSUER");
    }

    @Test
    void constructor_rejectsInvalidTtl() {
        assertThatThrownBy(() -> service(SECRET, ISSUER, null, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_ACCESS_TTL");
        assertThatThrownBy(() -> service(SECRET, ISSUER, Duration.ZERO, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(SECRET, ISSUER, Duration.ofMinutes(-1), NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
