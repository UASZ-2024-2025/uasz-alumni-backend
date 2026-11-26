package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.jwt.ServiceTokenProvider;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import java.net.http.HttpClient;
import java.time.Duration;
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
 * Client de notification-messaging-service : {@code POST /notifications/send} (ALUM-119).
 * Jeton de service (ADR-0010), timeouts, retry sur erreurs transitoires uniquement (réseau, 5xx, 429).
 */
@Component
public class NotificationClient {

    static final String TARGET_SERVICE = "notification-messaging-service";

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public NotificationClient(@Value("${notification.base-url}") String baseUrl,
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
        maxRetriesString = "${notification.retry.max-retries}",
        delayString = "${notification.retry.delay-ms}",
        multiplierString = "${notification.retry.multiplier}",
        maxDelayString = "${notification.retry.max-delay-ms}")
    public void send(SendNotificationRequest request) {
        http.post()
            .uri("/notifications/send")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(TARGET_SERVICE))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }
}
