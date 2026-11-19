package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.jwt.ServiceTokenProvider;
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
 * Client de notification-messaging-service : {@code POST /notifications/send} (contrat SendNotificationRequest).
 *
 * <ul>
 *   <li>Authentification par jeton de service (ADR-0010), jamais par le JWT de l'utilisateur (ALUM-83).</li>
 *   <li>Timeouts de connexion et de lecture : un service lent ne bloque pas un thread indéfiniment.</li>
 *   <li>Retry avec délai croissant sur les erreurs transitoires uniquement : réseau / timeout, 5xx, 429.
 *       Une erreur 4xx n'est pas rejouée, ni un 501 (opération pas encore implémentée par le service appelé).</li>
 * </ul>
 */
@Component
public class NotificationClient {

    static final String TARGET_SERVICE = "notification-messaging-service";
    static final String SEND_PATH = "/notifications/send";

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
            .uri(SEND_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(TARGET_SERVICE))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }
}
