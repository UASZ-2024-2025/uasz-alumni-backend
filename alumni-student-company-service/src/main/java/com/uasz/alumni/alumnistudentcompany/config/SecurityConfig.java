package com.uasz.alumni.alumnistudentcompany.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API stateless, JWT validés localement (jetons utilisateurs et jetons de service, voir {@code JwtConfig}).
 *
 * <ul>
 *   <li>{@code /internal/**} : réservé aux autres microservices (jeton de service, rôle SERVICE, ADR-0010) ;
 *       un JWT utilisateur y reçoit 403.</li>
 *   <li>Le reste de l'API : réservé aux utilisateurs ; un jeton de service n'y a pas accès. Les règles
 *       plus fines par endpoint (propriétaire du profil, administrateur) relèvent de chaque fonctionnalité.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] USER_ROLES = {"ETUDIANT", "ALUMNI", "ENSEIGNANT", "ENTREPRISE", "ADMINISTRATEUR"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${security.api.prefix}") String p,
                                            AuthenticationManagerResolver<HttpServletRequest> jwtResolver)
            throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(p + "/internal/**").hasRole("SERVICE")
                .anyRequest().hasAnyRole(USER_ROLES))
            .oauth2ResourceServer(o -> o.authenticationManagerResolver(jwtResolver));
        return http.build();
    }
}
