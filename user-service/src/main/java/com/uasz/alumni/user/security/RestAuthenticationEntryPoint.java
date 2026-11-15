package com.uasz.alumni.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uasz.alumni.user.spi.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ErrorResponse error = new ErrorResponse();
        error.setCode("UNAUTHORIZED");
        error.setMessage(authException.getMessage() != null ? authException.getMessage() : "Authentification requise ou invalide");
        error.setTimestamp(OffsetDateTime.now());

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}

