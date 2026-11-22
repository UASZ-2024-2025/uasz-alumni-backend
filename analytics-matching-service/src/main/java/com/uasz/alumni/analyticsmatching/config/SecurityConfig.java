package com.uasz.alumni.analyticsmatching.config;

import com.uasz.alumni.analyticsmatching.jwt.JwtAuthenticationFilter;
import com.uasz.alumni.analyticsmatching.jwt.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Sécurité du service : API stateless, sondes de santé publiques, tout le reste authentifié
 * par JWT ({@link JwtAuthenticationFilter}). Les règles par rôle sont posées sur les contrôleurs
 * avec {@code @PreAuthorize}.
 *
 * <p>{@code proxyTargetClass = true} : les contrôleurs implémentent les interfaces du SPI ; sans
 * proxy de classe, {@code @PreAuthorize} produirait un proxy JDK sans {@code @RestController},
 * et la route ne serait plus enregistrée (constaté en {@code @WebMvcTest}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(proxyTargetClass = true)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentification requise ou invalide.\"}");
                })
                // Réponse écrite directement : le gestionnaire par défaut passe par sendError(403), donc par /error,
                // où le filtre JWT ne s'exécute pas ; la requête y devenait anonyme et repartait en 401
                // (le front croyait alors la session expirée). MockMvc ne passe pas par /error : visible en prod seulement.
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.setCharacterEncoding("UTF-8");
                    res.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Accès refusé.\"}");
                }))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
