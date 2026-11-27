package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Profils étudiants d'alumni-student-company-service : {@code GET /students/{id}}, lu avant d'envoyer une
 * proposition (ALUM-174).
 *
 * <ul>
 *   <li>Le JWT du recruteur est propagé (ADR-0006) : la route est ouverte à tout utilisateur connecté.</li>
 *   <li>Mêmes timeouts et retry que {@link CompanyClient}. Un étudiant inconnu (404) donne un résultat vide ;
 *       une erreur de communication remonte telle quelle ({@code RestClientException}).</li>
 * </ul>
 */
@Component
public class StudentDirectory {

    static final String STUDENT_PATH = "/students/{id}";

    private final RestClient http;

    public StudentDirectory(@Value("${company.base-url}") String baseUrl,
                                @Value("${company.connect-timeout}") Duration connectTimeout,
                                @Value("${company.read-timeout}") Duration readTimeout) {
        HttpClient jdkClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(readTimeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * @param id identifiant du profil ou de l'utilisateur étudiant
     * @return l'étudiant, ou vide s'il est inconnu
     */
    @Retryable(
        includes = {ResourceAccessException.class, HttpServerErrorException.class,
                    HttpClientErrorException.TooManyRequests.class},
        excludes = HttpServerErrorException.NotImplemented.class,
        maxRetriesString = "${company.retry.max-retries}",
        delayString = "${company.retry.delay-ms}",
        multiplierString = "${company.retry.multiplier}",
        maxDelayString = "${company.retry.max-delay-ms}")
    public Optional<StudentRef> find(UUID id) {
        try {
            StudentProfile profile = http.get()
                    .uri(STUDENT_PATH, id)
                    .header(HttpHeaders.AUTHORIZATION, currentAuthorization())
                    .retrieve()
                    .body(StudentProfile.class);
            if (profile == null || profile.getUserId() == null) {
                return Optional.empty();
            }
            String name = Stream.of(profile.getFirstName(), profile.getLastName())
                    .filter(part -> part != null && !part.isBlank())
                    .map(String::strip)
                    .collect(Collectors.joining(" "));
            return Optional.of(new StudentRef(profile.getUserId(), name.isEmpty() ? null : name));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /** Étudiant résolu : {@code userId} est l'identifiant utilisé par les notifications et les listes. */
    public record StudentRef(UUID userId, String fullName) { }

    /** En-tête Authorization de la requête en cours (déjà validé par le filtre JWT). */
    private static String currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String header = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null) {
                return header;
            }
        }
        throw new IllegalStateException("Aucun jeton à propager : appel hors requête HTTP");
    }
}
