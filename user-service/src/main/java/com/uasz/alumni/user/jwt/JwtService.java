package com.uasz.alumni.user.jwt;

import com.uasz.alumni.user.config.JwtProperties;
import com.uasz.alumni.user.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Émission et validation des access tokens (JWT HMAC). Claims : sub = id utilisateur (UUID),
 * role, email. Les autres microservices valident signature + émetteur avec le même JWT_SECRET / JWT_ISSUER.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        String secret = props.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) manquant : lancez 'make setup'");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) doit être encodé en Base64", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret doit faire au moins 32 octets une fois décodé");
        }
        if (props.issuer() == null || props.issuer().isBlank()) {
            throw new IllegalStateException("jwt.issuer (JWT_ISSUER) manquant");
        }
        if (props.accessTtl() == null || props.accessTtl().isZero() || props.accessTtl().isNegative()) {
            throw new IllegalStateException("jwt.access-ttl (JWT_ACCESS_TTL) invalide");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = props.issuer();
        this.accessTtl = props.accessTtl();
        this.clock = clock;
    }

    public String generateAccessToken(UserAccount user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Id utilisateur si le jeton est valide (signature, émetteur, expiration), sinon vide. */
    public Optional<UUID> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
