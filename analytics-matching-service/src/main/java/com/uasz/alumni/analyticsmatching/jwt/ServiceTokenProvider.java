package com.uasz.alumni.analyticsmatching.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Jetons de service pour les appels inter-services (ADR-0010).
 * Claims : {@code iss} = émetteur interne, {@code sub} = nom de ce service, {@code aud} = service appelé,
 * {@code role} = SERVICE. Mis en cache par audience et renouvelé 30 s avant expiration.
 */
@Component
public class ServiceTokenProvider {

    public static final String SERVICE_ROLE = "SERVICE";
    private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofSeconds(30);

    private final SecretKey key;
    private final String issuer;
    private final String serviceName;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    @Autowired
    public ServiceTokenProvider(@Value("${jwt.secret}") String secret,
                                @Value("${jwt.issuer}") String userIssuer,
                                @Value("${service-token.issuer}") String issuer,
                                @Value("${spring.application.name}") String serviceName,
                                @Value("${service-token.ttl}") Duration ttl) {
        this(secret, userIssuer, issuer, serviceName, ttl, Clock.systemUTC());
    }

    ServiceTokenProvider(String secret, String userIssuer, String issuer, String serviceName, Duration ttl,
                         Clock clock) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret.trim()));
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("service-token.issuer manquant");
        }
        if (issuer.equals(userIssuer)) {
            throw new IllegalStateException("service-token.issuer doit être différent de jwt.issuer");
        }
        this.issuer = issuer;
        this.serviceName = serviceName;
        this.ttl = ttl;
        this.clock = clock;
    }

    public String tokenFor(String targetService) {
        Instant now = clock.instant();
        CachedToken cached = cache.get(targetService);
        if (cached != null && cached.expiresAt().isAfter(now.plus(RENEW_BEFORE_EXPIRY))) {
            return cached.token();
        }
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(serviceName)
                .audience().add(targetService).and()
                .claim("role", SERVICE_ROLE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        cache.put(targetService, new CachedToken(token, expiresAt));
        return token;
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
