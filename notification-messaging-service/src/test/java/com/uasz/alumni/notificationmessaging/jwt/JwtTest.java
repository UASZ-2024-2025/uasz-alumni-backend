package com.uasz.alumni.notificationmessaging.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER = Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));
    private static final String ISSUER = "alumni-uasz-user";

    private final JwtService service = new JwtService(SECRET, ISSUER);
    private final UUID id = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private String token(String secret, String issuer, Instant exp, String role) {
        return Jwts.builder().issuer(issuer).subject(id.toString()).claim("role", role).claim("email", "a@uasz.sn")
                .issuedAt(new Date()).expiration(Date.from(exp))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))).compact();
    }

    private String valid() {
        return token(SECRET, ISSUER, Instant.now().plusSeconds(600), "ALUMNI");
    }

    @Test
    void jetonValideDonneLUtilisateur() {
        assertThat(service.parse(valid())).contains(new AuthenticatedUser(id, "a@uasz.sn", "ALUMNI"));
    }

    @Test
    void jetonInvalideEstRefuse() {
        assertThat(service.parse(token(OTHER, ISSUER, Instant.now().plusSeconds(600), "ALUMNI"))).isEmpty();
        assertThat(service.parse(token(SECRET, "autre", Instant.now().plusSeconds(600), "ALUMNI"))).isEmpty();
        assertThat(service.parse(token(SECRET, ISSUER, Instant.now().minusSeconds(60), "ALUMNI"))).isEmpty();
        assertThat(service.parse(token(SECRET, ISSUER, Instant.now().plusSeconds(600), " "))).isEmpty();
        assertThat(service.parse("n-importe-quoi")).isEmpty();
    }

    @Test
    void filtreAuthentifieAvecUnJetonValide() throws Exception {
        run("Bearer " + valid());
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ALUMNI");
    }

    @Test
    void filtreIgnoreUnJetonInvalideOuAbsent() throws Exception {
        run("Bearer abc");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        run(null);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void run(String header) throws Exception {
        var req = new MockHttpServletRequest();
        if (header != null) {
            req.addHeader("Authorization", header);
        }
        FilterChain chain = (r, s) -> { };
        new JwtAuthenticationFilter(service).doFilter(req, new MockHttpServletResponse(), chain);
    }
}
