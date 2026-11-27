package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniEmploymentRecord;
import com.uasz.alumni.analyticsmatching.jwt.ServiceTokenProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Client d'alumni-student-company-service : {@code GET /internal/alumni/employment} (ALUM-183).
 *
 * <ul>
 *   <li>Authentification par jeton de service (ADR-0010), jamais par le JWT de l'utilisateur.</li>
 *   <li>Lecture synchrone (ADR-0006) : timeouts courts et un seul retry, sur les erreurs transitoires seulement
 *       (réseau / timeout, 5xx, 429). Un 501 n'est pas rejoué.</li>
 *   <li>Les filtres passent par des variables d'URI : leurs caractères spéciaux (&amp;, +...) sont encodés.</li>
 * </ul>
 */
@Component
public class AlumniEmploymentClient {

    static final String TARGET_SERVICE = "alumni-student-company-service";
    static final String EMPLOYMENT_PATH = "/internal/alumni/employment";

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public AlumniEmploymentClient(@Value("${student-company.base-url}") String baseUrl,
                                  @Value("${student-company.connect-timeout}") Duration connectTimeout,
                                  @Value("${student-company.read-timeout}") Duration readTimeout,
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
        maxRetriesString = "${student-company.retry.max-retries}",
        delayString = "${student-company.retry.delay-ms}",
        multiplierString = "${student-company.retry.multiplier}",
        maxDelayString = "${student-company.retry.max-delay-ms}")
    public List<AlumniEmploymentRecord> list(Integer graduationYear, String specialty) {
        List<AlumniEmploymentRecord> records = http.get()
                .uri(builder -> {
                    builder.path(EMPLOYMENT_PATH);
                    Map<String, Object> variables = new HashMap<>();
                    if (graduationYear != null) {
                        builder.queryParam("graduationYear", "{graduationYear}");
                        variables.put("graduationYear", graduationYear);
                    }
                    if (specialty != null) {
                        builder.queryParam("specialty", "{specialty}");
                        variables.put("specialty", specialty);
                    }
                    return builder.build(variables);
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(TARGET_SERVICE))
                .retrieve()
                .body(new ParameterizedTypeReference<List<AlumniEmploymentRecord>>() { });
        return records == null ? List.of() : records;
    }
}
