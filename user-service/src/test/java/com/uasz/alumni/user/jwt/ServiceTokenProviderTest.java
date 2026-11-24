package com.uasz.alumni.user.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.user.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class ServiceTokenProviderTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final JwtProperties JWT = new JwtProperties(SECRET, "alumni-uasz-user", Duration.ofMinutes(15));
    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    private static ServiceTokenProvider provider(Clock clock) {
        return new ServiceTokenProvider(JWT, "alumni-uasz-internal", "user-service", Duration.ofMinutes(5), clock);
    }

    private static Jws<Claims> parse(String token, Instant at) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .clock(() -> Date.from(at))
                .build()
                .parseSignedClaims(token);
    }

    @Test
    void issuesAServiceTokenForTheTargetService() {
        String token = provider(Clock.fixed(NOW, ZoneOffset.UTC)).tokenFor("alumni-student-company-service");

        Jws<Claims> jws = parse(token, NOW);
        Claims claims = jws.getPayload();
        // HS256 : seul algorithme accepté par les services destinataires (NimbusJwtDecoder)
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getIssuer()).isEqualTo("alumni-uasz-internal");
        assertThat(claims.getSubject()).isEqualTo("user-service");
        assertThat(claims.getAudience()).containsExactly("alumni-student-company-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void reusesTheCachedTokenThenRenewsItBeforeExpiry() {
        MutableClock clock = new MutableClock(NOW);
        ServiceTokenProvider provider = provider(clock);
        String first = provider.tokenFor("alumni-student-company-service");

        clock.now = NOW.plus(Duration.ofMinutes(4));
        assertThat(provider.tokenFor("alumni-student-company-service")).isEqualTo(first);

        clock.now = NOW.plus(Duration.ofMinutes(4)).plusSeconds(31); // moins de 30 s avant l'expiration
        assertThat(provider.tokenFor("alumni-student-company-service")).isNotEqualTo(first);
    }

    @Test
    void oneTokenPerAudience() {
        ServiceTokenProvider provider = provider(Clock.fixed(NOW, ZoneOffset.UTC));

        String a = provider.tokenFor("alumni-student-company-service");
        String b = provider.tokenFor("notification-messaging-service");

        assertThat(parse(b, NOW).getPayload().getAudience()).containsExactly("notification-messaging-service");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void refusesAServiceIssuerEqualToTheUserIssuer() {
        // sinon un jeton de service serait accepté partout comme un jeton utilisateur
        assertThatThrownBy(() -> new ServiceTokenProvider(JWT, "alumni-uasz-user", "user-service",
                Duration.ofMinutes(5), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
