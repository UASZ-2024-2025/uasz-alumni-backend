package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSummary;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Client de notification-messaging-service : nombre de messages non lus de l'utilisateur (ALUM-246).
 *
 * <ul>
 *   <li>Lit {@code GET /messages/conversations} et {@code GET /messages/groups} et additionne leurs
 *       {@code unreadCount} : aucun endpoint ni aucune donnée ajoutés côté messagerie.</li>
 *   <li>Le JWT de l'utilisateur est propagé (ADR-0006) : la messagerie ne renvoie que ses propres fils.</li>
 *   <li>Timeouts de connexion et de lecture ; retry sur les erreurs transitoires uniquement
 *       (réseau / timeout, 5xx, 429).</li>
 * </ul>
 */
@Component
public class MessagingClient {

    static final String CONVERSATIONS_PATH = "/messages/conversations";
    static final String GROUPS_PATH = "/messages/groups";

    private final RestClient http;

    public MessagingClient(@Value("${notification-messaging-service.base-url}") String baseUrl,
                           @Value("${notification-messaging-service.connect-timeout}") Duration connectTimeout,
                           @Value("${notification-messaging-service.read-timeout}") Duration readTimeout) {
        HttpClient jdkClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(readTimeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Non-lus des conversations privées et des groupes de l'utilisateur dont le jeton est fourni. */
    @Retryable(
        includes = {ResourceAccessException.class, HttpServerErrorException.class,
                    HttpClientErrorException.TooManyRequests.class},
        excludes = HttpServerErrorException.NotImplemented.class,
        maxRetriesString = "${notification-messaging-service.retry.max-retries}",
        delayString = "${notification-messaging-service.retry.delay-ms}",
        multiplierString = "${notification-messaging-service.retry.multiplier}",
        maxDelayString = "${notification-messaging-service.retry.max-delay-ms}")
    public int unreadMessages(String bearerToken) {
        List<ConversationSummary> conversations = get(CONVERSATIONS_PATH, bearerToken,
                new ParameterizedTypeReference<>() { });
        List<GroupSummary> groups = get(GROUPS_PATH, bearerToken, new ParameterizedTypeReference<>() { });
        long total = sum(conversations, ConversationSummary::getUnreadCount)
                + sum(groups, GroupSummary::getUnreadCount);
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    private <T> List<T> get(String path, String bearerToken, ParameterizedTypeReference<List<T>> type) {
        return http.get()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .retrieve()
            .body(type);
    }

    /** Somme des non-lus ; un compteur absent (null) ou négatif compte pour 0. */
    private static <T> long sum(List<T> items, Function<T, Integer> unread) {
        if (items == null) {
            return 0;
        }
        return items.stream()
            .map(unread)
            .filter(Objects::nonNull)
            .mapToLong(count -> Math.max(0, count))
            .sum();
    }
}
