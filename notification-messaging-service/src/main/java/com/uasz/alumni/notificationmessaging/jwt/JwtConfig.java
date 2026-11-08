package com.uasz.alumni.notificationmessaging.jwt;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

/**
 * Deux familles de jetons sont acceptées, choisies selon l'émetteur ({@code iss}) :
 * <ul>
 *   <li><b>utilisateur</b> : émis par user-service ({@code jwt.issuer}), {@code sub} = UUID, {@code role} = RoleName ;</li>
 *   <li><b>service</b> (ADR-0010) : émetteur interne ({@code service-token.issuer}), {@code role} = SERVICE,
 *       {@code aud} doit contenir le nom de ce service.</li>
 * </ul>
 * Un émetteur inconnu est refusé (401).
 */
@Configuration
public class JwtConfig {

    @Bean
    SecretKey jwtSigningKey(@Value("${jwt.secret}") String secret) {
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> jwtAuthenticationManagerResolver(
            SecretKey jwtSigningKey,
            JwtAuthenticationConverter converter,
            @Value("${jwt.issuer}") String userIssuer,
            @Value("${service-token.issuer}") String serviceIssuer,
            @Value("${spring.application.name}") String serviceName) {

        NimbusJwtDecoder userDecoder = decoder(jwtSigningKey);
        userDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(userIssuer));

        NimbusJwtDecoder serviceDecoder = decoder(jwtSigningKey);
        serviceDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(serviceIssuer),
                audienceContains(serviceName)));

        Map<String, AuthenticationManager> managers = Map.of(
                userIssuer, manager(userDecoder, converter),
                serviceIssuer, manager(serviceDecoder, converter));
        return new JwtIssuerAuthenticationManagerResolver(managers::get);
    }

    private static NimbusJwtDecoder decoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static AuthenticationManager manager(NimbusJwtDecoder decoder, JwtAuthenticationConverter converter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return provider::authenticate;
    }

    static OAuth2TokenValidator<Jwt> audienceContains(String audience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "Jeton de service non destiné à " + audience, null);
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }
}
