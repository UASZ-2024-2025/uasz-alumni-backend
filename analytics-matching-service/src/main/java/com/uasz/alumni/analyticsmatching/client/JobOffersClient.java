package com.uasz.alumni.analyticsmatching.client;

import com.uasz.alumni.joboffer.spi.model.JobOffer;
import com.uasz.alumni.joboffer.spi.model.JobOfferPagedResponse;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Offres de job-offer-service, lues pour le matching (ALUM-180, ALUM-181).
 * JWT de l'appelant propagé (ADR-0006), timeouts courts ; toute erreur remonte ({@code RestClientException}).
 */
@Component
public class JobOffersClient {

    static final int PAGE_SIZE = 100;
    /** Garde-fou : au-delà, les offres suivantes ne sont pas évaluées. */
    static final int MAX_PAGES = 20;

    private final RestClient http;

    public JobOffersClient(@Value("${clients.job-offer.base-url}") String baseUrl,
                           @Value("${clients.connect-timeout}") Duration connectTimeout,
                           @Value("${clients.read-timeout}") Duration readTimeout) {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(readTimeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Toutes les offres ouvertes (OPEN, date limite non dépassée), page par page. */
    public List<JobOffer> listOpen() {
        List<JobOffer> offers = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            int current = page;
            JobOfferPagedResponse response = http.get()
                    .uri(uri -> uri.path("/offers")
                            .queryParam("status", OfferStatus.OPEN.getValue())
                            .queryParam("page", current)
                            .queryParam("size", PAGE_SIZE)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, CallerToken.authorization())
                    .retrieve()
                    .body(JobOfferPagedResponse.class);
            if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                break;
            }
            offers.addAll(response.getContent());
            Integer total = response.getTotalElements();
            if (total != null && offers.size() >= total) {
                break;
            }
        }
        return offers;
    }
}
