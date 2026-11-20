package com.uasz.alumni.joboffer.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ALUM-168 / ALUM-148 : claims name et companyId du jeton utilisateur. */
class JwtCompanyClaimTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String ISSUER = "alumni-uasz-user";

    private final JwtService service = new JwtService(SECRET, ISSUER);
    private final UUID id = UUID.randomUUID();

    private String token(String companyId) {
        var builder = Jwts.builder().issuer(ISSUER).subject(id.toString()).claim("role", "ENTREPRISE")
                .claim("email", "rh@gainde.sn").claim("name", "Awa Diop")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)));
        if (companyId != null) {
            builder.claim("companyId", companyId);
        }
        return builder.signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
    }

    @Test
    void leJetonDonneLeNomEtLaFicheEntreprise() {
        UUID companyId = UUID.randomUUID();

        assertThat(service.parse(token(companyId.toString())))
                .contains(new AuthenticatedUser(id, "rh@gainde.sn", "ENTREPRISE", "Awa Diop", companyId));
    }

    @Test
    void sansCompanyIdLeCompteNEstPasRattache() {
        assertThat(service.parse(token(null))).hasValueSatisfying(u -> assertThat(u.companyId()).isNull());
    }

    @Test
    void unCompanyIdMalFormeRendLeJetonInvalide() {
        assertThat(service.parse(token("pas-un-uuid"))).isEmpty();
    }
}
