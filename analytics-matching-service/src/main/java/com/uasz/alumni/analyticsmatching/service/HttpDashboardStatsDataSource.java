package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.PlatformStats;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.jwt.ServiceTokenProvider;
import com.uasz.alumni.joboffer.spi.model.JobOfferPagedResponse;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Récupère les chiffres clés directement auprès des services sources :
 * <ul>
 *   <li>{@code alumni-student-company-service} : {@code GET /internal/stats} (jeton de service, ADR-0010)</li>
 *   <li>{@code job-offer-service} : {@code GET /offers?type=INTERNSHIP|JOB&page=0&size=1}</li>
 * </ul>
 */
@Component
public class HttpDashboardStatsDataSource implements DashboardStatsDataSource {

    static final String STUDENT_COMPANY_SERVICE = "alumni-student-company-service";

    private final RestClient studentCompanyClient;
    private final RestClient jobOfferClient;
    private final ServiceTokenProvider tokens;

    public HttpDashboardStatsDataSource(
            @Value("${clients.student-company.base-url}") String studentCompanyBaseUrl,
            @Value("${clients.job-offer.base-url}") String jobOfferBaseUrl,
            @Value("${clients.connect-timeout}") Duration connectTimeout,
            @Value("${clients.read-timeout}") Duration readTimeout,
            ServiceTokenProvider tokens) {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(readTimeout);
        this.studentCompanyClient = RestClient.builder().baseUrl(studentCompanyBaseUrl).requestFactory(factory).build();
        this.jobOfferClient = RestClient.builder().baseUrl(jobOfferBaseUrl).requestFactory(factory).build();
        this.tokens = tokens;
    }

    @Override
    public PlatformCounts fetchCounts() {
        try {
            PlatformStats scStats = studentCompanyClient.get()
                    .uri("/internal/stats")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(STUDENT_COMPANY_SERVICE))
                    .retrieve()
                    .body(PlatformStats.class);

            int internships = fetchOfferTotal(OfferType.INTERNSHIP);
            int jobs = fetchOfferTotal(OfferType.JOB);

            return new PlatformCounts(
                    safeInt(scStats != null ? scStats.getAlumniCount() : null),
                    safeInt(scStats != null ? scStats.getEmployedAlumniCount() : null),
                    safeInt(scStats != null ? scStats.getStudentCount() : null),
                    safeInt(scStats != null ? scStats.getCompanyCount() : null),
                    internships,
                    jobs);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                    "Impossible de récupérer les statistiques auprès des services sources");
        }
    }

    private int fetchOfferTotal(OfferType type) {
        JobOfferPagedResponse page = jobOfferClient.get()
                .uri(uriBuilder -> uriBuilder.path("/offers")
                        .queryParam("type", type.name())
                        .queryParam("page", 0)
                        .queryParam("size", 1)
                        .build())
                .retrieve()
                .body(JobOfferPagedResponse.class);
        return page != null && page.getTotalElements() != null ? page.getTotalElements() : 0;
    }

    private static int safeInt(Long value) {
        if (value == null) {
            return 0;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
