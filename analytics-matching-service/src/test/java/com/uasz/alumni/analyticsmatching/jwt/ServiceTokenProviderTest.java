package com.uasz.alumni.analyticsmatching.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class ServiceTokenProviderTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    private static ServiceTokenProvider provider() {
        return new ServiceTokenProvider(SECRET, "alumni-uasz-user", "alumni-uasz-internal",
                "analytics-matching-service", Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void emetUnJetonDeServicePourLeServiceCible() {
        String token = provider().tokenFor("alumni-student-company-service");

        Jws<Claims> jws = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .clock(() -> Date.from(NOW)).build().parseSignedClaims(token);
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(jws.getPayload().getIssuer()).isEqualTo("alumni-uasz-internal");
        assertThat(jws.getPayload().getSubject()).isEqualTo("analytics-matching-service");
        assertThat(jws.getPayload().getAudience()).containsExactly("alumni-student-company-service");
        assertThat(jws.getPayload().get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    void reutiliseLeJetonEnCacheParAudience() {
        ServiceTokenProvider provider = provider();

        assertThat(provider.tokenFor("alumni-student-company-service"))
                .isEqualTo(provider.tokenFor("alumni-student-company-service"))
                .isNotEqualTo(provider.tokenFor("job-offer-service"));
    }

    @Test
    void refuseUnEmetteurDeServiceEgalAL_emetteurUtilisateur() {
        assertThatThrownBy(() -> new ServiceTokenProvider(SECRET, "alumni-uasz-user", "alumni-uasz-user",
                "analytics-matching-service", Duration.ofMinutes(5), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
