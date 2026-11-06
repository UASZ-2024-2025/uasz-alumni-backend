package com.uasz.alumni.user.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuration de sécurité de base : API stateless, sondes de santé publiques,
 * tout le reste authentifié. La validation JWT et les règles par endpoint
 * (selon les contrats) seront ajoutées lors de l'implémentation.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String apiPrefix;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${security.api.prefix}") String apiPrefix,
            @Value("${security.cors.allowed-origins}") String allowedOrigins) {
        this.apiPrefix = apiPrefix;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
    }

    /** Autorise le frontend (ex. http://localhost:4200) à appeler l'API depuis le navigateur. */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
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
