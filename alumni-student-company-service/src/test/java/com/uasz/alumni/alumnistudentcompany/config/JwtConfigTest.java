package com.uasz.alumni.alumnistudentcompany.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtConfigTest {

    private static final byte[] CLE = "01234567890123456789012345678901".getBytes();
    private static final String SECRET = Base64.getEncoder().encodeToString(CLE);
    private final JwtConfig config = new JwtConfig();
    private final JwtDecoder decoder = config.jwtDecoder(SECRET, "alumni-uasz-user");

    private String jeton(String issuer, byte[] cle) throws Exception {
        var claims = new JWTClaimsSet.Builder().subject("u1").issuer(issuer).claim("role", "ALUMNI")
            .expirationTime(new Date(System.currentTimeMillis() + 60_000)).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(cle));
        return jwt.serialize();
    }

    @Test
    void accepteUnJetonValide() throws Exception {
        Jwt jwt = decoder.decode(jeton("alumni-uasz-user", CLE));
        var auth = config.jwtAuthenticationConverter().convert(jwt);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ALUMNI")));
    }

    @Test
    void refuseUnMauvaisEmetteur() throws Exception {
        String t = jeton("autre", CLE);
        assertThrows(JwtException.class, () -> decoder.decode(t));
    }

    @Test
    void refuseUneMauvaiseSignature() throws Exception {
        String t = jeton("alumni-uasz-user", "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345".getBytes());
        assertThrows(JwtException.class, () -> decoder.decode(t));
    }

    @Test
    void sansClaimRoleAucuneAutorite() {
        Jwt jwt = Jwt.withTokenValue("x").header("alg", "HS256").subject("u1").build();
        assertTrue(config.jwtAuthenticationConverter().convert(jwt).getAuthorities().stream()
            .noneMatch(a -> a.getAuthority().startsWith("ROLE_")));
    }
}
