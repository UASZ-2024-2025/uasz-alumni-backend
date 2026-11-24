
package com.uasz.alumni.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class RestSecurityErrorHandlerTest {

    private final RestSecurityErrorHandler handler = new RestSecurityErrorHandler();

    @Test
    void commence_writes401_inErrorResponseFormat() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .matches("\\{\"code\":\"UNAUTHORIZED\",\"message\":\"[^\"]+\",\"timestamp\":\"[^\"]+\"}");
    }

    @Test
    void handle_writes403_inErrorResponseFormat() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .matches("\\{\"code\":\"ACCESS_DENIED\",\"message\":\"[^\"]+\",\"timestamp\":\"[^\"]+\"}");
    }
}
