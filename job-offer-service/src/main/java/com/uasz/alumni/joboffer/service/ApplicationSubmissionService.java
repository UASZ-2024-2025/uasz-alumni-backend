package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.JobApplication;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.repository.JobApplicationRepository;
import com.uasz.alumni.joboffer.repository.OfferRepository;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.spi.model.ApplicationCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ALUM-172 : dépôt d'une candidature par l'étudiant et suivi de ses candidatures.
 *
 * <ul>
 *   <li>Seul un ETUDIANT postule, et seulement pour lui-même ({@code studentId} = son compte).</li>
 *   <li>L'offre doit être OPEN et son échéance non dépassée ; une seule candidature par offre (409).</li>
 *   <li>Le recruteur (auteur de l'offre) est notifié après le commit.</li>
 * </ul>
 */
@Service
public class ApplicationSubmissionService {

    static final int MAX_COVER_LETTER = 5000;
    static final int MAX_PAGE_SIZE = 100;
    private static final String STUDENT = "ETUDIANT";
    private static final String ADMIN = "ADMINISTRATEUR";

    private final OfferRepository offers;
    private final JobApplicationRepository applications;
    private final ApplicationEventPublisher events;

    public ApplicationSubmissionService(OfferRepository offers, JobApplicationRepository applications,
                                        ApplicationEventPublisher events) {
        this.offers = offers;
        this.applications = applications;
        this.events = events;
    }

    @Transactional
    public JobApplication submit(UUID offerId, ApplicationCreateRequest request, AuthenticatedUser caller) {
        if (!STUDENT.equals(caller.role())) {
            throw forbidden("Seul un étudiant peut postuler à une offre");
        }
        if (request.getStudentId() != null && !request.getStudentId().equals(caller.id())) {
            throw forbidden("Un étudiant ne peut postuler que pour lui-même");
        }
        Offer offer = offers.findById(offerId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offre introuvable : " + offerId));
        LocalDate deadline = offer.getDeadline();
        if (offer.getStatus() != OfferStatus.OPEN || (deadline != null && deadline.isBefore(LocalDate.now()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OFFER_NOT_OPEN",
                    "Cette offre n'accepte plus de candidatures");
        }
        if (applications.existsByOfferIdAndStudentId(offerId, caller.id())) {
            throw alreadyApplied();
        }
        String coverLetter = request.getCoverLetter() == null ? null : request.getCoverLetter().trim();
        if (coverLetter != null && coverLetter.length() > MAX_COVER_LETTER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "coverLetter : " + MAX_COVER_LETTER + " caractères maximum");
        }

        JobApplication application = new JobApplication(offer, caller.id(), displayName(caller), request.getCvId(),
                coverLetter == null || coverLetter.isEmpty() ? null : coverLetter);
        try {
            applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            // double clic : la contrainte uq_applications_offer_student a refusé la seconde insertion
            throw alreadyApplied();
        }
        events.publishEvent(new ApplicationSubmitted(application.getId(), offer.getPublisherId(), offer.getTitle(),
                application.getStudentName()));
        return application;
    }

    /**
     * Étudiant : ses propres candidatures uniquement ({@code studentId} absent ou égal à son compte).
     * Administrateur : celles d'un étudiant, ou toutes.
     */
    @Transactional(readOnly = true)
    public Page<JobApplication> listMine(UUID studentId, ApplicationStatus status, Integer page, Integer size,
                                         AuthenticatedUser caller) {
        UUID target;
        if (STUDENT.equals(caller.role())) {
            if (studentId != null && !studentId.equals(caller.id())) {
                throw forbidden("Un étudiant ne consulte que ses propres candidatures");
            }
            target = caller.id();
        } else if (ADMIN.equals(caller.role())) {
            target = studentId;
        } else {
            throw forbidden("Seuls les étudiants et l'administration consultent ce suivi");
        }

        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "page doit être positif et size compris entre 1 et " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        if (target == null) {
            return status == null ? applications.findAllBy(pageable) : applications.findByStatus(status, pageable);
        }
        return status == null
                ? applications.findByStudentId(target, pageable)
                : applications.findByStudentIdAndStatus(target, status, pageable);
    }

    private static String displayName(AuthenticatedUser caller) {
        return caller.name() != null && !caller.name().isBlank() ? caller.name().trim() : caller.email();
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private static ApiException alreadyApplied() {
        return new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS", "Vous avez déjà postulé à cette offre");
    }
}
