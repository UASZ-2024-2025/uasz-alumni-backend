package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.ApplicationEntity;
import com.uasz.alumni.joboffer.model.JobOfferEntity;
import com.uasz.alumni.joboffer.repository.ApplicationRepository;
import com.uasz.alumni.joboffer.repository.JobOfferRepository;
import com.uasz.alumni.joboffer.security.CurrentUser;
import com.uasz.alumni.joboffer.spi.model.ApplicationCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final JobOfferRepository offers;
    private final ApplicationRepository applications;
    private final ApplicationEventPublisher events;

    public ApplicationService(JobOfferRepository offers, ApplicationRepository applications,
                              ApplicationEventPublisher events) {
        this.offers = offers;
        this.applications = applications;
        this.events = events;
    }

    @Transactional
    public ApplicationEntity submit(CurrentUser student, UUID offerId, ApplicationCreateRequest req) {
        JobOfferEntity offer = offers.findById(offerId).orElseThrow(() -> OfferService.notFound(offerId));
        if (!offer.acceptsApplications(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OFFER_CLOSED",
                    "Cette offre n'accepte plus de candidatures");
        }
        if (applications.existsByOfferIdAndStudentUserId(offerId, student.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_APPLIED", "Vous avez déjà postulé à cette offre");
        }
        // TODO : vérifier que req.studentId / req.cvId appartiennent à l'étudiant (alumni-student-company-service)
        return applications.save(new ApplicationEntity(offer, req.getStudentId(), student.id(), req.getCvId(),
                req.getCoverLetter()));
    }

    @Transactional(readOnly = true)
    public List<ApplicationEntity> listForOffer(CurrentUser user, UUID offerId) {
        JobOfferEntity offer = offers.findById(offerId).orElseThrow(() -> OfferService.notFound(offerId));
        requireRecruiter(user, offer);
        return applications.findByOfferIdOrderByCreatedAtAsc(offerId);
    }

    /**
     * Change le statut d'une candidature (publieur de l'offre ou administrateur) et publie
     * {@link ApplicationStatusChanged}. La notification part après le commit, sans bloquer la réponse.
     */
    @Transactional
    public ApplicationEntity updateStatus(CurrentUser user, UUID applicationId, ApplicationStatus newStatus) {
        ApplicationEntity application = applications.findWithOfferById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND",
                        "Candidature introuvable : " + applicationId));
        requireRecruiter(user, application.getOffer());
        if (application.changeStatus(newStatus)) {
            events.publishEvent(new ApplicationStatusChanged(application.getId(), application.getStudentUserId(),
                    application.getOffer().getTitle(), newStatus));
        }
        return application;
    }

    private static void requireRecruiter(CurrentUser user, JobOfferEntity offer) {
        if (!user.isAdmin() && !offer.getPublisherId().equals(user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_OFFER_OWNER",
                    "Seul le publieur de l'offre ou un administrateur peut gérer ses candidatures");
        }
    }
}
