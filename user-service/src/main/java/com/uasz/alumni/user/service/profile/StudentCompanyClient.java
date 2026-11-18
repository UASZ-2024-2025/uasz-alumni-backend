package com.uasz.alumni.user.service.profile;

import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.user.jwt.ServiceTokenProvider;
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
 * Client d'alumni-student-company-service : {@code POST /internal/profiles} (ALUM-83).
 *
 * <ul>
 *   <li>Authentification par jeton de service (ADR-0010), jamais par le JWT de l'utilisateur.</li>
 *   <li>Timeouts de connexion et de lecture : un service lent ne bloque pas un thread indéfiniment.</li>
 *   <li>Retry avec délai croissant sur les erreurs transitoires uniquement : réseau / timeout,
 *       5xx, 429. Une erreur 4xx (requête invalide, jeton refusé) n'est pas rejouée, ni un 501
 *       (opération pas encore implémentée par le service appelé : rejouer ne changerait rien).</li>
 *   <li>L'opération est idempotente côté serveur (un profil par {@code userId}) : rejouer après un
 *       timeout ne crée pas de doublon.</li>
 * </ul>
 */
@Component
public class StudentCompanyClient {

    static final String TARGET_SERVICE = "alumni-student-company-service";
    static final String PROVISION_PATH = "/internal/profiles";

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public StudentCompanyClient(@Value("${student-company.base-url}") String baseUrl,
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
    public void provisionProfile(ProfileProvisioningRequest request) {
        http.post()
            .uri(PROVISION_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(TARGET_SERVICE))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }
}
