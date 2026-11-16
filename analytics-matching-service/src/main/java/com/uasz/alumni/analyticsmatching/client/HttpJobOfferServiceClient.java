package com.uasz.alumni.analyticsmatching.client;

import com.uasz.alumni.joboffer.spi.model.JobOffer;
import com.uasz.alumni.joboffer.spi.model.JobOfferPagedResponse;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpJobOfferServiceClient implements JobOfferServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpJobOfferServiceClient.class);

    private final RestClient restClient;

    public HttpJobOfferServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.job-offer-service.url:http://localhost:8083}") String jobOfferServiceUrl,
            @Value("${security.api.prefix:/v1}") String apiPrefix) {
        this.restClient = restClientBuilder
                .baseUrl(jobOfferServiceUrl + apiPrefix)
                .build();
    }

    @Override
    public List<JobOffer> getJobOffers(OfferStatus status) {
        try {
            JobOfferPagedResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/offers")
                            .queryParam("status", status != null ? status.name() : "OPEN")
                            .queryParam("size", 100)
                            .build())
                    .retrieve()
                    .body(JobOfferPagedResponse.class);

            if (response != null && response.getContent() != null) {
                return response.getContent();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des offres depuis job-offer-service", e);
            return Collections.emptyList();
        }
    }
}
