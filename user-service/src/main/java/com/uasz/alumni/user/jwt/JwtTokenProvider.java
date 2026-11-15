package com.uasz.alumni.user.jwt;

import com.uasz.alumni.user.model.UserEntity;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final String secret;
    private final String issuer;
    private final long accessTokenValidityInMs = 3600 * 1000L; // 1 heure
    private final long refreshTokenValidityInMs = 7 * 24 * 3600 * 1000L; // 7 jours

    public JwtTokenProvider(
            @Value("${jwt.secret:default-secret-at-least-32-chars-long-secret-key-123456}") String secret,
            @Value("${jwt.issuer:alumni-uasz-user}") String issuer) {
        this.secret = secret;
        this.issuer = issuer;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            try {
                keyBytes = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 non disponible", e);
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserEntity user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenValidityInMs);

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(issuer)
                .claim("email", user.getEmail())
                .claim("role", user.getRoleName() != null ? user.getRoleName().name() : null)
                .claim("status", user.getStatus() != null ? user.getStatus().name() : null)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(UserEntity user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenValidityInMs);

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(issuer)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public long getAccessTokenValidityInSeconds() {
        return accessTokenValidityInMs / 1000L;
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            if (!issuer.equals(claims.getIssuer())) {
                log.warn("Issuer invalide dans le token: {}", claims.getIssuer());
                return false;
            }
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.debug("Token JWT invalide : {}", e.getMessage());
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    public RoleName extractRole(String token) {
        String roleStr = extractAllClaims(token).get("role", String.class);
        return roleStr != null ? RoleName.fromValue(roleStr) : null;
    }

    public UserStatus extractStatus(String token) {
        String statusStr = extractAllClaims(token).get("status", String.class);
        return statusStr != null ? UserStatus.fromValue(statusStr) : null;
    }
}

