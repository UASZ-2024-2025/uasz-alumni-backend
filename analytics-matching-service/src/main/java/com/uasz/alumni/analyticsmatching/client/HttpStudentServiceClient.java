package com.uasz.alumni.analyticsmatching.client;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentPagedResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class HttpStudentServiceClient implements StudentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpStudentServiceClient.class);

    private final RestClient restClient;

    public HttpStudentServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.student-service.url:http://localhost:8082}") String studentServiceUrl,
            @Value("${security.api.prefix:/v1}") String apiPrefix) {
        this.restClient = restClientBuilder
                .baseUrl(studentServiceUrl + apiPrefix)
                .build();
    }

    @Override
    public Optional<StudentProfile> getStudentProfile(UUID studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        try {
            StudentProfile profile = restClient.get()
                    .uri("/students/{id}", studentId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw HttpClientErrorException.create(
                                response.getStatusCode(),
                                response.getStatusText(),
                                response.getHeaders(),
                                null,
                                null);
                    })
                    .body(StudentProfile.class);

            return Optional.ofNullable(profile);
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Étudiant introuvable dans alumni-student-company-service : {}", studentId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Erreur lors de l'appel à alumni-student-company-service pour l'étudiant {}", studentId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<StudentProfile> getAllStudents() {
        try {
            StudentPagedResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/students")
                            .queryParam("size", 100)
                            .build())
                    .retrieve()
                    .body(StudentPagedResponse.class);

            if (response != null && response.getContent() != null) {
                return response.getContent();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des étudiants depuis alumni-student-company-service", e);
            return Collections.emptyList();
        }
    }
}
