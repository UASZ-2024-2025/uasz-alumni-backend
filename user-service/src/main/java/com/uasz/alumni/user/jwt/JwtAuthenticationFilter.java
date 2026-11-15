package com.uasz.alumni.user.jwt;

import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.AuthenticatedUser;
import com.uasz.alumni.user.spi.model.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
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
 * immédiatement dans user-service. Instancié dans SecurityConfig (pas un @Component : évite la double inscription).
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

            jwtService.parseUserId(header.substring(7).trim())
                    .flatMap(users::findById)
                    .filter(u -> u.getStatus() == UserStatus.ACTIF && !u.isLockedAt(clock.instant()))
                    .ifPresent(u -> {
                        AuthenticatedUser principal = new AuthenticatedUser(u.getId(), u.getEmail(), u.getRole());
                        var auth = UsernamePasswordAuthenticationToken.authenticated(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(auth);
                        SecurityContextHolder.setContext(context);
                    });
        }
        chain.doFilter(request, response);
    }
}
