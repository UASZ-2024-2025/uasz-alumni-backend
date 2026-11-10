package com.uasz.alumni.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite le nombre d'appels par adresse IP sur les endpoints publics qui envoient un email
 * ({@code POST /auth/register} et {@code POST /auth/resend-confirmation}), pour éviter le spam
 * et le bourrage de comptes. Fenêtre fixe, en mémoire : suffisant pour une instance ; avec
 * plusieurs réplicas, la limite s'applique par réplica (une limite partagée demanderait Redis
 * ou la passerelle d'API).
 *
 * <p>L'adresse IP vient de {@code getRemoteAddr()} : derrière un ingress de confiance,
 * {@code server.forward-headers-strategy=native} y place la vraie IP du client.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int count) { }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final String registerPath;
    private final String resendPath;
    private final int maxRequests;
    private final Duration window;
    private final Clock clock;

    public RateLimitFilter(@Value("${security.api.prefix}") String apiPrefix,
                           @Value("${app.rate-limit.max-requests:10}") int maxRequests,
                           @Value("${app.rate-limit.window-seconds:60}") long windowSeconds,
                           Clock clock) {
        this.registerPath = apiPrefix + "/auth/register";
        this.resendPath = apiPrefix + "/auth/resend-confirmation";
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(registerPath.equals(path) || resendPath.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> isExpired(w, now));
        }

        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        Window current = windows.merge(key, new Window(now, 1),
                (old, fresh) -> isExpired(old, now) ? fresh : new Window(old.start(), old.count() + 1));

        if (current.count() > maxRequests) {
            long retryAfter = Math.max(1, Duration.between(now, current.start().plus(window)).toSeconds());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"TOO_MANY_REQUESTS\",\"message\":"
                    + "\"Trop de tentatives. Réessayez dans quelques instants.\",\"timestamp\":\"" + now + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isExpired(Window w, Instant now) {
        return !w.start().plus(window).isAfter(now);
    }
}
