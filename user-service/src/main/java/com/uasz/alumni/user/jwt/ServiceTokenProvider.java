package com.uasz.alumni.user.jwt;

import com.uasz.alumni.user.config.JwtProperties;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Jetons de service pour les appels inter-services (ADR-0010).
 *
 * <p>Claims : {@code iss} = émetteur interne (distinct de l'émetteur des jetons utilisateurs),
 * {@code sub} = nom du service appelant, {@code aud} = service appelé, {@code role} = SERVICE,
 * durée de vie courte. Signés en HS256 avec le secret partagé {@code JWT_SECRET}, comme dans
 * job-offer-service. Un jeton est mis en cache par audience et renouvelé avant son expiration.
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

    public ServiceTokenProvider(JwtProperties jwt,
                                @Value("${service-token.issuer}") String issuer,
                                @Value("${spring.application.name}") String serviceName,
                                @Value("${service-token.ttl}") Duration ttl,
                                Clock clock) {
        // Le format du secret est déjà contrôlé au démarrage par JwtService.
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwt.secret().trim()));
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("service-token.issuer manquant");
        }
        if (issuer.equals(jwt.issuer())) {
            // Sinon un jeton de service serait accepté partout comme un jeton utilisateur.
            throw new IllegalStateException("service-token.issuer doit être différent de jwt.issuer");
        }
        this.issuer = issuer;
        this.serviceName = serviceName;
        this.ttl = ttl;
        this.clock = clock;
    }

    /** Jeton valide pour appeler le service {@code audience}. */
    public String tokenFor(String audience) {
        Instant now = clock.instant();
        CachedToken cached = cache.get(audience);
        if (cached != null && now.isBefore(cached.expiresAt().minus(RENEW_BEFORE_EXPIRY))) {
            return cached.value();
        }
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(serviceName)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .claim("role", SERVICE_ROLE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                // HS256 imposé : les services destinataires n'acceptent que cet algorithme.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        cache.put(audience, new CachedToken(token, expiresAt));
        return token;
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
