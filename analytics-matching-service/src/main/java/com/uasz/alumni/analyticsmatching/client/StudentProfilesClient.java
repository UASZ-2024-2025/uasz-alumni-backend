package com.uasz.alumni.analyticsmatching.client;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentPagedResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    static final int PAGE_SIZE = 100;
    /** Garde-fou : au-delà, les profils suivants ne sont pas évalués. */
    static final int MAX_PAGES = 20;

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

    /** Tous les profils étudiants, page par page ({@code GET /students}). */
    public List<StudentProfile> listAll() {
        List<StudentProfile> profiles = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            int current = page;
            StudentPagedResponse response = http.get()
                    .uri(uri -> uri.path("/students").queryParam("page", current).queryParam("size", PAGE_SIZE).build())
                    .header(HttpHeaders.AUTHORIZATION, CallerToken.authorization())
                    .retrieve()
                    .body(StudentPagedResponse.class);
            if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                break;
            }
            profiles.addAll(response.getContent());
            Number total = response.getTotalElements();
            if (total != null && profiles.size() >= total.longValue()) {
                break;
            }
        }
        return profiles;
    }
}
