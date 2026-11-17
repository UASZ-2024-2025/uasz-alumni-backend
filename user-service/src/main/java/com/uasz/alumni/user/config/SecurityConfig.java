package com.uasz.alumni.user.config;

import com.uasz.alumni.user.jwt.JwtAuthenticationFilter;
import com.uasz.alumni.user.jwt.JwtService;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.RestSecurityErrorHandler;
import com.uasz.alumni.user.spi.model.RoleName;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API stateless. Les règles "ADMINISTRATEUR uniquement" sont ici (visibles d'un coup d'œil) ;
 * les règles "soi-même ou ADMINISTRATEUR" sont dans UserService (elles dépendent de l'id ciblé).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN = RoleName.ADMINISTRATEUR.name();

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
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtService jwtService,
                                            UserAccountRepository users,
                                            Clock clock,
                                            RestSecurityErrorHandler errorHandler) throws Exception {

        String[] publicPosts = Stream.of(
                        "/auth/register", "/auth/login", "/auth/refresh-token",
                        "/auth/forgot-password", "/auth/reset-password",
                        "/auth/confirm-email", "/auth/resend-confirmation")
                .map(p -> apiPrefix + p)
                .toArray(String[]::new);

        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.POST, publicPosts).permitAll()
                // ADMINISTRATEUR uniquement
                .requestMatchers(HttpMethod.GET, apiPrefix + "/users").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, apiPrefix + "/users").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PATCH, apiPrefix + "/users/*/status").hasRole(ADMIN)
                .requestMatchers(HttpMethod.DELETE, apiPrefix + "/users/*").hasRole(ADMIN)
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, users, clock),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
