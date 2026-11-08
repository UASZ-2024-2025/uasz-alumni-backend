package com.uasz.alumni.joboffer.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Jetons de service pour les appels inter-services (ADR-0010).
 *
 * <p>Claims : {@code iss} = émetteur interne (distinct de user-service), {@code sub} = nom du service appelant,
 * {@code aud} = service appelé, {@code role} = SERVICE, durée de vie courte. Un jeton est mis en cache par
 * audience et renouvelé avant son expiration.
 */
@Component
public class ServiceTokenProvider {

    public static final String SERVICE_ROLE = "SERVICE";
    private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofSeconds(30);

    private final JwtEncoder encoder;
    private final String issuer;
    private final String serviceName;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    @Autowired
    public ServiceTokenProvider(SecretKey jwtSigningKey,
                                @Value("${service-token.issuer}") String issuer,
                                @Value("${spring.application.name}") String serviceName,
                                @Value("${service-token.ttl}") Duration ttl) {
        this(jwtSigningKey, issuer, serviceName, ttl, Clock.systemUTC());
    }

    ServiceTokenProvider(SecretKey key, String issuer, String serviceName, Duration ttl, Clock clock) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
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
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(serviceName)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", SERVICE_ROLE)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        cache.put(audience, new CachedToken(token, expiresAt));
        return token;
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
