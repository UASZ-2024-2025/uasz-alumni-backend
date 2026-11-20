package com.uasz.alumni.user.jwt;

import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.AuthenticatedUser;
import com.uasz.alumni.user.security.ServiceCaller;
import com.uasz.alumni.user.spi.model.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Jeton invalide ou compte non actif : on n'authentifie simplement pas (401 via RestSecurityErrorHandler
 * sur les routes protégées). Le rôle vient de la BASE, pas du jeton : une suspension est effective
 * immédiatement dans user-service. Un jeton de service valide pour ce service (ADR-0010) authentifie un
 * {@link ServiceCaller} avec le rôle SERVICE (routes {@code /internal/**}).
 * Instancié dans SecurityConfig (pas un @Component : évite la double inscription).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository users;
    private final Clock clock;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository users, Clock clock) {
        this.jwtService = jwtService;
        this.users = users;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(7).trim();
            Optional<UUID> userId = jwtService.parseUserId(token);

            if (userId.isPresent()) {
                userId.flatMap(users::findById)
                        .filter(u -> u.getStatus() == UserStatus.ACTIF && !u.isLockedAt(clock.instant()))
                        .ifPresent(u -> authenticate(
                                new AuthenticatedUser(u.getId(), u.getEmail(), u.getRole()),
                                u.getRole().name(), request));
            } else {
                jwtService.parseServiceCaller(token)
                        .ifPresent(service -> authenticate(
                                new ServiceCaller(service), JwtService.SERVICE_ROLE, request));
            }
        }
        chain.doFilter(request, response);
    }

    private static void authenticate(Object principal, String role, HttpServletRequest request) {
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }
}
