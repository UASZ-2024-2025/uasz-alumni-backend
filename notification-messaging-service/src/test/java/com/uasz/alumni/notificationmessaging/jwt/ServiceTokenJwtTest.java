package com.uasz.alumni.notificationmessaging.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Jetons de service (ADR-0010) : émetteur interne, rôle SERVICE, audience = ce service. */
class ServiceTokenJwtTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String USER_ISSUER = "alumni-uasz-user";
    private static final String SERVICE_ISSUER = "alumni-uasz-internal";
    private static final String SELF = "notification-messaging-service";

    private final JwtService service = new JwtService(SECRET, USER_ISSUER, SERVICE_ISSUER, SELF);

    private static String token(String issuer, String subject, String audience, String role) {
        var builder = Jwts.builder().issuer(issuer).subject(subject).claim("role", role)
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(300)));
        if (audience != null) {
            builder.audience().add(audience);
        }
        return builder.signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256).compact();
    }

    @Test
    void aServiceTokenForThisServiceGivesTheServiceRole() {
        assertThat(service.parse(token(SERVICE_ISSUER, "user-service", SELF, "SERVICE")))
                .contains(new AuthenticatedUser(null, null, "SERVICE"));
    }

    @Test
    void aServiceTokenForAnotherServiceIsRejected() {
        assertThat(service.parse(token(SERVICE_ISSUER, "user-service", "alumni-student-company-service", "SERVICE")))
                .isEmpty();
    }

    @Test
    void aServiceTokenWithoutAudienceIsRejected() {
        assertThat(service.parse(token(SERVICE_ISSUER, "user-service", null, "SERVICE"))).isEmpty();
    }

    @Test
    void theInternalIssuerCannotCarryAUserRole() {
        assertThat(service.parse(token(SERVICE_ISSUER, "user-service", SELF, "ADMINISTRATEUR"))).isEmpty();
    }

    @Test
    void aUserTokenCannotClaimTheServiceRole() {
        assertThat(service.parse(token(USER_ISSUER, UUID.randomUUID().toString(), null, "SERVICE"))).isEmpty();
    }

    @Test
    void userTokensStillWork() {
        UUID id = UUID.randomUUID();
        assertThat(service.parse(token(USER_ISSUER, id.toString(), null, "ALUMNI")))
                .hasValueSatisfying(u -> {
                    assertThat(u.id()).isEqualTo(id);
                    assertThat(u.role()).isEqualTo("ALUMNI");
                });
    }

    @Test
    void anUnknownIssuerIsRejected() {
        assertThat(service.parse(token("pirate", "user-service", SELF, "SERVICE"))).isEmpty();
    }

    @Test
    void withoutServiceIssuerConfiguredServiceTokensAreRejected() {
        JwtService usersOnly = new JwtService(SECRET, USER_ISSUER);
        assertThat(usersOnly.parse(token(SERVICE_ISSUER, "user-service", SELF, "SERVICE"))).isEmpty();
    }

    @Test
    void refusesIdenticalIssuers() {
        assertThatThrownBy(() -> new JwtService(SECRET, USER_ISSUER, USER_ISSUER, SELF))
                .isInstanceOf(IllegalStateException.class);
    }
}
