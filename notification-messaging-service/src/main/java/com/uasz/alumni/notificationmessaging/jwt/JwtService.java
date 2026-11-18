package com.uasz.alumni.notificationmessaging.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Validation des jetons reçus (même JWT_SECRET dans tous les services). Deux familles, choisies selon l'émetteur :
 * <ul>
 *   <li><b>utilisateur</b> : émis par user-service ({@code jwt.issuer}). Claims lus : sub = id utilisateur, role, email.
 *       Le rôle vient du jeton : une suspension n'est donc effective ici qu'à l'expiration du jeton
 *       (durée courte, voir JWT_ACCESS_TTL).</li>
 *   <li><b>service</b> (ADR-0010) : émetteur interne ({@code service-token.issuer}), {@code role} = SERVICE et
 *       {@code aud} contenant le nom de ce service. Le principal n'a ni id ni email.</li>
 * </ul>
 * Tout autre émetteur, ou un jeton de service destiné à un autre service, est refusé.
 */
@Service
public class JwtService {

    public static final String SERVICE_ROLE = "SERVICE";

    private final SecretKey key;
    private final String issuer;
    private final String serviceIssuer;
    private final String serviceName;

    /** Jetons utilisateurs seulement (tests unitaires du filtre). */
    public JwtService(String secret, String issuer) {
        this(secret, issuer, null, null);
    }

    @Autowired
    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.issuer}") String issuer,
                      @Value("${service-token.issuer}") String serviceIssuer,
                      @Value("${spring.application.name}") String serviceName) {
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
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("jwt.issuer (JWT_ISSUER) manquant");
        }
        if (serviceIssuer != null && serviceIssuer.equals(issuer)) {
            // Sinon un jeton de service serait lu comme un jeton utilisateur (et inversement).
            throw new IllegalStateException("service-token.issuer doit être différent de jwt.issuer");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = issuer;
        this.serviceIssuer = serviceIssuer;
        this.serviceName = serviceName;
    }

    /** Appelant du jeton si signature, émetteur et expiration sont valides, sinon vide. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                return Optional.empty();
            }
            if (issuer.equals(claims.getIssuer())) {
                if (SERVICE_ROLE.equals(role)) {
                    return Optional.empty(); // un jeton utilisateur ne porte jamais le rôle SERVICE
                }
                return Optional.of(new AuthenticatedUser(
                        UUID.fromString(claims.getSubject()), claims.get("email", String.class), role));
            }
            if (serviceIssuer != null && serviceIssuer.equals(claims.getIssuer())) {
                Set<String> audience = claims.getAudience();
                if (SERVICE_ROLE.equals(role) && audience != null && audience.contains(serviceName)) {
                    return Optional.of(new AuthenticatedUser(null, null, SERVICE_ROLE));
                }
            }
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
