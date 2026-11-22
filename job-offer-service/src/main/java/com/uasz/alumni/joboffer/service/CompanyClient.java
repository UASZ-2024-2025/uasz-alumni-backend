package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.joboffer.jwt.ServiceTokenProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
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
 * Client d'alumni-student-company-service : {@code GET /internal/companies/{id}} (contrat Company, ALUM-148).
 *
 * <ul>
 *   <li>Authentification par jeton de service (ADR-0010), jamais par le JWT de l'utilisateur.</li>
 *   <li>Lecture synchrone (ADR-0006) : timeouts courts et un seul retry, sur les erreurs transitoires seulement
 *       (réseau / timeout, 5xx, 429). Une fiche inconnue (404) donne un résultat vide, sans retry.</li>
 * </ul>
 */
@Component
public class CompanyClient {

    static final String TARGET_SERVICE = "alumni-student-company-service";
    static final String COMPANY_PATH = "/internal/companies/{id}";

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public CompanyClient(@Value("${company.base-url}") String baseUrl,
                         @Value("${company.connect-timeout}") Duration connectTimeout,
                         @Value("${company.read-timeout}") Duration readTimeout,
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
        maxRetriesString = "${company.retry.max-retries}",
        delayString = "${company.retry.delay-ms}",
        multiplierString = "${company.retry.multiplier}",
        maxDelayString = "${company.retry.max-delay-ms}")
    public Optional<Company> find(UUID companyId) {
        try {
            return Optional.ofNullable(http.get()
                    .uri(COMPANY_PATH, companyId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(TARGET_SERVICE))
                    .retrieve()
                    .body(Company.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
