package com.uasz.alumni.joboffer.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Fabrique de vrais JWT signés (même secret que application-test.yaml), comme ceux de user-service. */
public final class TestTokens {

    public static final String SECRET = "dGVzdHMtb25seS1zZWNyZXQtMDEyMzQ1Njc4OS1hYmNkZWZnaGlqa2xtbm9w";
    public static final SecretKeySpec KEY = new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256");

    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));

    private TestTokens() {
    }

    public static String user(UUID userId, String role) {
        return token("alumni-uasz-user", userId.toString(), role);
    }

    public static String token(String issuer, String subject, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(subject).issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("role", role).build();
        return ENCODER.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
