package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.JobOfferEntity;
import com.uasz.alumni.joboffer.repository.JobOfferRepository;
import com.uasz.alumni.joboffer.security.CurrentUser;
import com.uasz.alumni.joboffer.spi.model.JobOfferCreateRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferService {

    private final JobOfferRepository offers;

    public OfferService(JobOfferRepository offers) {
        this.offers = offers;
    }

    @Transactional
    public JobOfferEntity create(CurrentUser publisher, JobOfferCreateRequest req) {
        // TODO : récupérer companyName / publisherName auprès de alumni-student-company-service et user-service
        JobOfferEntity offer = new JobOfferEntity(publisher.id(), req.getTitle(), req.getType(),
                req.getDescription(), req.getCompanyId());
        offer.setLocation(req.getLocation());
        offer.setRemoteType(req.getRemoteType());
        offer.setRequiredSkills(req.getRequiredSkills());
        offer.setDurationMonths(req.getDurationMonths());
        offer.setSalaryRange(req.getSalaryRange());
        offer.setDeadline(req.getDeadline());
        return offers.save(offer);
    }

    @Transactional(readOnly = true)
    public JobOfferEntity get(UUID id) {
        return offers.findWithSkillsById(id).orElseThrow(() -> notFound(id));
    }

    static ApiException notFound(UUID id) {
        return new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offre introuvable : " + id);
    }
}
