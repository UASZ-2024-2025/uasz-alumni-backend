package com.uasz.alumni.notificationmessaging.config;

import com.uasz.alumni.notificationmessaging.jwt.JwtAuthenticationFilter;
import com.uasz.alumni.notificationmessaging.jwt.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * API stateless, sondes de santé publiques. Jetons validés par JwtService (ALUM-28), qui accepte aussi les
 * jetons de service (ADR-0010).
 * <ul>
 *   <li>{@code POST /notifications/send} : réservé aux autres microservices (rôle SERVICE) et aux
 *       administrateurs (ALUM-83) ; un autre utilisateur reçoit 403.</li>
 *   <li>Le reste : réservé aux utilisateurs (un jeton de service y reçoit 403). Les règles fines sont
 *       portées par {@code @PreAuthorize} sur les contrôleurs.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(proxyTargetClass = true) // proxy CGLIB : le contrôleur reste détecté par Spring MVC
public class SecurityConfig {

    private static final String[] USER_ROLES = {"ETUDIANT", "ALUMNI", "ENSEIGNANT", "ENTREPRISE", "ADMINISTRATEUR"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                            @Value("${security.api.prefix}") String p) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.POST, p + "/notifications/send")
                    .hasAnyRole(JwtService.SERVICE_ROLE, "ADMINISTRATEUR")
                .anyRequest().hasAnyRole(USER_ROLES))
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentification requise ou invalide.\"}");
            }))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
