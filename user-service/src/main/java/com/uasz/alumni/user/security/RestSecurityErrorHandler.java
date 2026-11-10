package com.uasz.alumni.user.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 401 / 403 produits par Spring Security (avant d'atteindre un controller), au format ErrorResponse
 * du contrat : {code, message, timestamp}. Messages sans guillemets doubles (JSON écrit à la main).
 */
@Component
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, 401, "UNAUTHORIZED", "Authentification requise ou invalide.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, 403, "ACCESS_DENIED", "Accès refusé : permissions insuffisantes.");
    }

    private static void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}"
                .formatted(code, message, OffsetDateTime.now()));
    }
}
