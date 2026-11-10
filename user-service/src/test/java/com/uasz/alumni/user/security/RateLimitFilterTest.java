package com.uasz.alumni.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-09T10:00:00Z"));
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        filter = new RateLimitFilter("/v1", 3, 60, clock);
    }

    @Test
    void blocksTheRequestOverTheLimitWith429AndRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/v1/auth/register", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call("POST", "/v1/auth/register", "10.0.0.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("TOO_MANY_REQUESTS");
    }

    @Test
    void countsEachAddressAndEachEndpointSeparately() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("POST", "/v1/auth/register", "10.0.0.1");
        }
        assertThat(call("POST", "/v1/auth/register", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/v1/auth/resend-confirmation", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void allowsRequestsAgainOnceTheWindowHasPassed() throws Exception {
        for (int i = 0; i < 4; i++) {
            call("POST", "/v1/auth/register", "10.0.0.1");
        }
        now.set(now.get().plusSeconds(61));

        assertThat(call("POST", "/v1/auth/register", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresOtherEndpointsAndOtherMethods() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("POST", "/v1/auth/confirm-email", "10.0.0.1").getStatus()).isEqualTo(200);
            assertThat(call("GET", "/v1/auth/register", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse call(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
