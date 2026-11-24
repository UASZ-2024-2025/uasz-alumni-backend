package com.uasz.alumni.user.jwt;

import com.uasz.alumni.user.config.JwtProperties;
import com.uasz.alumni.user.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Émission et validation des access tokens (JWT HMAC). Claims : sub = id utilisateur (UUID),
 * role, email, name et, pour un compte ENTREPRISE rattaché à sa fiche, companyId (ALUM-168).
 * Les autres microservices valident signature + émetteur avec le même JWT_SECRET / JWT_ISSUER.
 *
 * <p>Valide aussi les jetons de service reçus sur {@code /internal/**} (ADR-0010) : émetteur interne,
 * rôle SERVICE et audience égale au nom de ce service.
 */
@Service
public class JwtService {

    public static final String SERVICE_ROLE = "SERVICE";
    public static final String CLAIM_COMPANY_ID = "companyId";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Clock clock;
    private final String serviceIssuer;
    private final String serviceName;

    /** Jetons utilisateurs seulement (tests unitaires) : les jetons de service sont refusés. */
    public JwtService(JwtProperties props, Clock clock) {
        this(props, clock, null, null);
    }

    @Autowired
    public JwtService(JwtProperties props, Clock clock,
                      @Value("${service-token.issuer}") String serviceIssuer,
                      @Value("${spring.application.name}") String serviceName) {
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
        if (serviceIssuer != null && serviceIssuer.equals(props.issuer())) {
            // Sinon un jeton de service serait lu comme un jeton utilisateur (et inversement).
            throw new IllegalStateException("service-token.issuer doit être différent de jwt.issuer");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = props.issuer();
        this.accessTtl = props.accessTtl();
        this.clock = clock;
        this.serviceIssuer = serviceIssuer;
        this.serviceName = serviceName;
    }

    public String generateAccessToken(UserAccount user) {
        Instant now = clock.instant();
        JwtBuilder builder = Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                // Nom affiché : les autres services (messagerie, groupes, offres) l'enregistrent sans appeler user-service.
                .claim("name", (user.getFirstName() + " " + user.getLastName()).trim());
        if (user.getCompanyId() != null) {
            // Fiche entreprise du compte (ALUM-168) : lue par job-offer-service sans appel à user-service.
            builder.claim(CLAIM_COMPANY_ID, user.getCompanyId().toString());
        }
        return builder
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

    /**
     * Nom du service appelant si le jeton est un jeton de service valide pour ce service (émetteur interne,
     * rôle SERVICE, audience = ce service), sinon vide.
     */
    public Optional<String> parseServiceCaller(String token) {
        if (serviceIssuer == null) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(serviceIssuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Set<String> audience = claims.getAudience();
            String caller = claims.getSubject();
            if (SERVICE_ROLE.equals(claims.get("role", String.class))
                    && audience != null && audience.contains(serviceName)
                    && caller != null && !caller.isBlank()) {
                return Optional.of(caller);
            }
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
