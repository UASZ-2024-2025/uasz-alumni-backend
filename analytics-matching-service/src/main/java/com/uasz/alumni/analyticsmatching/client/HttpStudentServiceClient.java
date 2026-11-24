package com.uasz.alumni.analyticsmatching.client;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class HttpStudentServiceClient implements StudentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpStudentServiceClient.class);

    private final RestClient restClient;

    public HttpStudentServiceClient(
            @Value("${services.student-service.url:http://localhost:8082}") String studentServiceUrl,
            @Value("${security.api.prefix:/v1}") String apiPrefix,
            @Value("${clients.connect-timeout:2s}") Duration connectTimeout,
            @Value("${clients.read-timeout:3s}") Duration readTimeout) {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(studentServiceUrl + apiPrefix)
                .requestFactory(factory)
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
}
