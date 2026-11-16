package com.uasz.alumni.analyticsmatching.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uasz.alumni.analyticsmatching.spi.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ErrorResponse error = new ErrorResponse();
        error.setCode("FORBIDDEN");
        error.setMessage(accessDeniedException.getMessage() != null ? accessDeniedException.getMessage() : "Accès refusé");
        error.setTimestamp(OffsetDateTime.now());

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}

