package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.jwt.ServiceTokenProvider;
import com.uasz.alumni.notificationmessaging.spi.model.UserUnreadCounts;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Client de notification-messaging-service : {@code GET /internal/users/{userId}/unread-counts} (ALUM-243).
 * Jeton de service (ADR-0010), jamais le JWT de l'utilisateur. Lecture synchrone : timeouts courts et un seul retry
 * sur les erreurs transitoires (ADR-0006).
 */
@Component
public class UnreadCountsClient {

    static final String TARGET_SERVICE = "notification-messaging-service";
    static final String UNREAD_PATH = "/internal/users/{userId}/unread-counts";

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public UnreadCountsClient(@Value("${notification.base-url}") String baseUrl,
                              @Value("${notification.connect-timeout}") Duration connectTimeout,
                              @Value("${notification.read-timeout}") Duration readTimeout,
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
        maxRetriesString = "${notification.unread.retry.max-retries}",
        delayString = "${notification.unread.retry.delay-ms}",
        multiplierString = "${notification.unread.retry.multiplier}",
        maxDelayString = "${notification.unread.retry.max-delay-ms}")
    public UserUnreadCounts unreadCounts(UUID userId) {
        return http.get()
            .uri(UNREAD_PATH, userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(TARGET_SERVICE))
            .retrieve()
            .body(UserUnreadCounts.class);
    }
}
