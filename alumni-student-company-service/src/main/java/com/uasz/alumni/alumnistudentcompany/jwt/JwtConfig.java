package com.uasz.alumni.alumnistudentcompany.jwt;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Validation des jetons émis par le user-service : signature HMAC (HS256) avec le secret partagé
 * (Base64), émetteur (iss) vérifié, expiration vérifiée.
 * Convention attendue : sub = userId (UUID) ; rôle(s) dans la claim "role" (texte) ou "roles" (liste),
 * ex. ETUDIANT, ALUMNI, ENSEIGNANT, ADMINISTRATEUR.
 */
@Configuration
public class JwtConfig {

    @Bean
    JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret, @Value("${jwt.issuer}") String issuer) {
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            String single = jwt.getClaimAsString("role");
            if (single != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + single));
            }
            List<String> many = jwt.getClaimAsStringList("roles");
            if (many != null) {
                many.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
            return authorities;
        });
        return converter;
    }
}
