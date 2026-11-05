package com.uasz.alumni.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de sécurité de base : API stateless, sondes de santé publiques,
 * tout le reste authentifié. La validation JWT et les règles par endpoint
 * (selon les contrats) seront ajoutées lors de l'implémentation.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String apiPrefix;

    public SecurityConfig(@Value("${security.api.prefix}") String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // Inscription, confirmation d'email, connexion, mot de passe oublié : accessibles sans jeton.
                // Le préfixe d'API varie (/v1 en local, /user-service/v1 sur Kubernetes).
                .requestMatchers(apiPrefix + "/auth/**").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
