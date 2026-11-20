package com.uasz.alumni.user.jwt;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.account;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.user.config.JwtProperties;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/** ALUM-168 : claim companyId du jeton utilisateur, jetons de service reçus (ADR-0010). */
class JwtServiceCompanyTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final SecretKey KEY = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
    private static final String USER_ISSUER = "alumni-uasz-user";
    private static final String SERVICE_ISSUER = "alumni-uasz-internal";
    private static final String SELF = "user-service";

    private final JwtService jwt = new JwtService(
            new JwtProperties(SECRET, USER_ISSUER, Duration.ofMinutes(15)),
            Clock.fixed(NOW, ZoneOffset.UTC), SERVICE_ISSUER, SELF);

    private Claims claimsOf(String token) {
        return Jwts.parser().verifyWith(KEY).build().parseSignedClaims(token).getPayload();
    }

    private static String serviceToken(String issuer, String audience, String role) {
        var builder = Jwts.builder().issuer(issuer).subject("alumni-student-company-service").claim("role", role)
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(300)));
        if (audience != null) {
            builder.audience().add(audience);
        }
        return builder.signWith(KEY, Jwts.SIG.HS256).compact();
    }

    @Test
    void leJetonPorteLeCompanyIdDuCompteRattache() {
        UserAccount account = account(UUID.randomUUID(), "rh@gainde.sn", RoleName.ENTREPRISE, UserStatus.ACTIF);
        UUID companyId = UUID.randomUUID();
        account.attachToCompany(companyId);

        Claims claims = claimsOf(jwt.generateAccessToken(account));

        assertThat(claims.get("companyId", String.class)).isEqualTo(companyId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ENTREPRISE");
    }

    @Test
    void sansRattachementLeJetonNePorteAucunCompanyId() {
        UserAccount account = account(UUID.randomUUID(), "rh@gainde.sn", RoleName.ENTREPRISE, UserStatus.ACTIF);

        assertThat(claimsOf(jwt.generateAccessToken(account)).containsKey("companyId")).isFalse();
    }

    @Test
    void unJetonDeServicePourCeServiceDonneLeNomDeLAppelant() {
        assertThat(jwt.parseServiceCaller(serviceToken(SERVICE_ISSUER, SELF, "SERVICE")))
                .contains("alumni-student-company-service");
    }

    @Test
    void unJetonDeServiceRefuseSiAudienceEmetteurOuRoleIncorrects() {
        assertThat(jwt.parseServiceCaller(serviceToken(SERVICE_ISSUER, "autre-service", "SERVICE"))).isEmpty();
        assertThat(jwt.parseServiceCaller(serviceToken(SERVICE_ISSUER, null, "SERVICE"))).isEmpty();
        assertThat(jwt.parseServiceCaller(serviceToken(SERVICE_ISSUER, SELF, "ADMINISTRATEUR"))).isEmpty();
        assertThat(jwt.parseServiceCaller(serviceToken("pirate", SELF, "SERVICE"))).isEmpty();
        assertThat(jwt.parseServiceCaller("n-importe-quoi")).isEmpty();
    }

    @Test
    void unJetonDeServiceNEstJamaisUnJetonUtilisateur() {
        assertThat(jwt.parseUserId(serviceToken(SERVICE_ISSUER, SELF, "SERVICE"))).isEmpty();
    }

    @Test
    void sansEmetteurDeServiceLesJetonsDeServiceSontRefuses() {
        JwtService usersOnly = new JwtService(new JwtProperties(SECRET, USER_ISSUER, Duration.ofMinutes(15)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(usersOnly.parseServiceCaller(serviceToken(SERVICE_ISSUER, SELF, "SERVICE"))).isEmpty();
    }

    @Test
    void refuseDesEmetteursIdentiques() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties(SECRET, USER_ISSUER, Duration.ofMinutes(15)),
                Clock.fixed(NOW, ZoneOffset.UTC), USER_ISSUER, SELF)).isInstanceOf(IllegalStateException.class);
    }
}
