package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.jwt.ServiceTokenProvider;
import com.uasz.alumni.user.spi.model.UserCompanyLinkRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Client de user-service : {@code PUT /internal/users/{id}/company} (ALUM-168).
 *
 * <ul>
 *   <li>Authentification par jeton de service (ADR-0010), jamais par le JWT de l'utilisateur.</li>
 *   <li>Timeouts de connexion et de lecture ; retry avec délai croissant sur les erreurs transitoires
 *       uniquement (réseau / timeout, 5xx, 429). Une erreur 4xx (404, 409...) n'est pas rejouée.</li>
 *   <li>L'opération est idempotente côté serveur : rejouer après un timeout ne change rien.</li>
 * </ul>
 */
@Component
public class UserServiceClient {

    static final String TARGET_SERVICE = "user-service";
    static final String LINK_PATH = "/internal/users/{userId}/company";

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public UserServiceClient(@Value("${user-service.base-url}") String baseUrl,
                             @Value("${user-service.connect-timeout}") Duration connectTimeout,
                             @Value("${user-service.read-timeout}") Duration readTimeout,
                             ServiceTokenProvider tokens) {
        HttpClient jdkClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(readTimeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.tokens = tokens;
    }

    @Retryable(
        includes = {ResourceAccessException.class, HttpServerErrorException.class,
                    HttpClientErrorException.TooManyRequests.class},
        excludes = HttpServerErrorException.NotImplemented.class,
        maxRetriesString = "${user-service.retry.max-retries}",
        delayString = "${user-service.retry.delay-ms}",
        multiplierString = "${user-service.retry.multiplier}",
        maxDelayString = "${user-service.retry.max-delay-ms}")
    public void linkCompany(UUID userId, UUID companyId) {
        http.put()
            .uri(LINK_PATH, userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(TARGET_SERVICE))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new UserCompanyLinkRequest().companyId(companyId))
            .retrieve()
            .toBodilessEntity();
    }
}
