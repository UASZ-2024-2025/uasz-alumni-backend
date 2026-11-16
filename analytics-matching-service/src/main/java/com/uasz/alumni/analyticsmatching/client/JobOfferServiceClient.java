package com.uasz.alumni.analyticsmatching.client;

import com.uasz.alumni.joboffer.spi.model.JobOffer;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import java.util.List;

public interface JobOfferServiceClient {
    List<JobOffer> getJobOffers(OfferStatus status);
}
