package com.uasz.alumni.notificationmessaging.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API stateless. {@code POST /notifications/send} est réservé aux services (jeton de service, ADR-0010)
 * et aux administrateurs ; le reste de l'API est réservé aux utilisateurs (un jeton de service n'y a pas accès).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] USER_ROLES = {"ETUDIANT", "ALUMNI", "ENSEIGNANT", "ADMINISTRATEUR"};

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
                .requestMatchers(HttpMethod.POST, p + "/notifications/send").hasAnyRole("SERVICE", "ADMINISTRATEUR")
                .anyRequest().hasAnyRole(USER_ROLES))
            .oauth2ResourceServer(o -> o.authenticationManagerResolver(jwtResolver));
        return http.build();
    }
}
