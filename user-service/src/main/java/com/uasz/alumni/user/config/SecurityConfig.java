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
 * API stateless, sondes de santé et endpoints d'authentification publics (hors logout),
 * tout le reste authentifié. La validation JWT sera ajoutée avec la logique métier.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${security.api.prefix}") String p) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(
                    p + "/auth/register",
                    p + "/auth/login",
                    p + "/auth/refresh-token",
                    p + "/auth/forgot-password",
                    p + "/auth/reset-password",
                    p + "/auth/confirm-email",
                    p + "/auth/resend-confirmation").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
