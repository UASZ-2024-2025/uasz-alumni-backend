package com.uasz.alumni.analyticsmatching.client;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Profils étudiants d'alumni-student-company-service, lus pour le matching (ALUM-180, ALUM-181).
 * JWT de l'appelant propagé (ADR-0006), timeouts courts. Un étudiant inconnu donne un résultat vide ;
 * toute autre erreur remonte ({@code RestClientException}).
 */
@Component
public class StudentProfilesClient {

    static final String STUDENT_PATH = "/students/{id}";

    private final RestClient http;

    public StudentProfilesClient(@Value("${clients.student-company.base-url}") String baseUrl,
                                 @Value("${clients.connect-timeout}") Duration connectTimeout,
                                 @Value("${clients.read-timeout}") Duration readTimeout) {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(readTimeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** @param id identifiant du profil ou de l'utilisateur étudiant */
    public Optional<StudentProfile> find(UUID id) {
        try {
            return Optional.ofNullable(http.get()
                    .uri(STUDENT_PATH, id)
                    .header(HttpHeaders.AUTHORIZATION, CallerToken.authorization())
                    .retrieve()
                    .body(StudentProfile.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
