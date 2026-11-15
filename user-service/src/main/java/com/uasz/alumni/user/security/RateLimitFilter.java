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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite le nombre d'appels par adresse IP sur les endpoints publics sensibles (POST uniquement).
 * <ul>
 *   <li>seuil strict ({@code app.rate-limit.max-requests}) : {@code register}, {@code resend-confirmation},
 *       {@code forgot-password}, {@code reset-password} (envoi d'emails, bourrage de comptes, essais de jetons) ;</li>
 *   <li>seuil « session » ({@code app.rate-limit.login-max-requests}, plus haut car plusieurs utilisateurs
 *       peuvent partager l'IP d'un campus) : {@code login}, {@code refresh-token}. Il limite surtout le
 *       verrouillage abusif de comptes tiers par envoi répété de mauvais mots de passe.</li>
 * </ul>
 * Chaque couple (IP, route) a son propre compteur. Fenêtre fixe, en mémoire : suffisant pour une instance ;
 * avec plusieurs réplicas, la limite s'applique par réplica (une limite partagée demanderait Redis
 * ou la passerelle d'API).
 *
 * <p>L'adresse IP vient de {@code getRemoteAddr()} : derrière un ingress de confiance,
 * {@code server.forward-headers-strategy=native} y place la vraie IP du client.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private static final List<String> STRICT_ROUTES = List.of(
            "/auth/register", "/auth/resend-confirmation", "/auth/forgot-password", "/auth/reset-password");
    private static final List<String> SESSION_ROUTES = List.of(
            "/auth/login", "/auth/refresh-token");

    private record Window(Instant start, int count) { }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    /** chemin complet (préfixe d'API compris) -> nombre maximal de requêtes par fenêtre */
    private final Map<String, Integer> limits;
    private final Duration window;
    private final Clock clock;

    public RateLimitFilter(@Value("${security.api.prefix}") String apiPrefix,
                           @Value("${app.rate-limit.max-requests:10}") int maxRequests,
                           @Value("${app.rate-limit.login-max-requests:30}") int loginMaxRequests,
                           @Value("${app.rate-limit.window-seconds:60}") long windowSeconds,
                           Clock clock) {
        Map<String, Integer> byPath = new HashMap<>();
        STRICT_ROUTES.forEach(route -> byPath.put(apiPrefix + route, maxRequests));
        SESSION_ROUTES.forEach(route -> byPath.put(apiPrefix + route, loginMaxRequests));
        this.limits = Map.copyOf(byPath);
        this.window = Duration.ofSeconds(windowSeconds);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !limits.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Integer maxRequests = limits.get(path);
        if (maxRequests == null) {
            chain.doFilter(request, response);
            return;
        }

        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> isExpired(w, now));
        }

        String key = request.getRemoteAddr() + "|" + path;
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
