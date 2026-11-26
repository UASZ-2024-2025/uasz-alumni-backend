package com.uasz.alumni.joboffer.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Validation des access tokens émis par user-service (même JWT_SECRET et même JWT_ISSUER dans tous les services).
 * Claims lus : sub = id utilisateur, role, email, name et companyId (fiche entreprise du compte ENTREPRISE, ALUM-168).
 * Le rôle vient du jeton : une suspension n'est donc effective ici qu'à l'expiration du jeton
 * (durée courte, voir JWT_ACCESS_TTL).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.issuer}") String issuer) {
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
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = issuer;
    }

    /** Utilisateur du jeton si signature, émetteur et expiration sont valides, sinon vide. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(token).getPayload();
            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                return Optional.empty();
            }
            String rawCompanyId = claims.get("companyId", String.class);
            UUID companyId = rawCompanyId == null || rawCompanyId.isBlank() ? null : UUID.fromString(rawCompanyId);
            return Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()), claims.get("email", String.class), role,
                    claims.get("name", String.class), companyId));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
