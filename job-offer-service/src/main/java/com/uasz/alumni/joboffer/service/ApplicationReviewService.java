package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.JobApplication;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.repository.JobApplicationRepository;
import com.uasz.alumni.joboffer.repository.OfferRepository;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AL-33 : traitement des candidatures reçues par le recruteur.
 * Seul l'auteur de l'offre (ou un administrateur) consulte et traite les candidatures ;
 * chaque changement de statut notifie l'étudiant.
 */
@Service
public class ApplicationReviewService {

    private final OfferRepository offers;
    private final JobApplicationRepository applications;
    private final ApplicationEventPublisher events;

    public ApplicationReviewService(OfferRepository offers, JobApplicationRepository applications,
                                    ApplicationEventPublisher events) {
        this.offers = offers;
        this.applications = applications;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<JobApplication> listForOffer(UUID offerId, Caller caller) {
        Offer offer = offers.findById(offerId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offre introuvable : " + offerId));
        checkCanManage(offer, caller);
        return applications.findByOfferIdOrderByCreatedAtDesc(offerId);
    }

    @Transactional
    public JobApplication changeStatus(UUID applicationId, ApplicationStatus newStatus, Caller caller) {
        JobApplication application = applications.findWithOfferById(applicationId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Candidature introuvable : " + applicationId));
        checkCanManage(application.getOffer(), caller);

        if (!JobApplication.isTransitionAllowed(application.getStatus(), newStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Transition de statut interdite : " + application.getStatus() + " -> " + newStatus);
        }
        application.changeStatus(newStatus);

        events.publishEvent(new ApplicationStatusChanged(application.getId(), application.getStudentId(),
                application.getOffer().getTitle(), newStatus, caller.bearerToken()));
        return application;
    }

    private static void checkCanManage(Offer offer, Caller caller) {
        if (!caller.admin() && !offer.isPublishedBy(caller.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Seul l'auteur de l'offre ou un administrateur peut traiter ses candidatures");
        }
    }

    /** Utilisateur courant, extrait du JWT par le contrôleur. */
    public record Caller(UUID userId, boolean admin, String bearerToken) {
    }
}
