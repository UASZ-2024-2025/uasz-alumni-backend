package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.exceptions.NotImplementedYetException;
import com.uasz.alumni.joboffer.model.JobApplication;
import com.uasz.alumni.joboffer.service.ApplicationReviewService;
import com.uasz.alumni.joboffer.service.ApplicationReviewService.Caller;
import com.uasz.alumni.joboffer.spi.api.ApplicationsApi;
import com.uasz.alumni.joboffer.spi.model.Application;
import com.uasz.alumni.joboffer.spi.model.ApplicationCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ApplicationPagedResponse;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatusUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationsController implements ApplicationsApi {

    private final ApplicationReviewService review;

    public ApplicationsController(ApplicationReviewService review) {
        this.review = review;
    }

    @Override
    public ResponseEntity<List<Application>> getApplicationsForOffer(UUID offerId) {
        return ResponseEntity.ok(review.listForOffer(offerId, currentCaller()).stream()
                .map(ApplicationsController::toDto).toList());
    }

    @Override
    public ResponseEntity<Application> submitApplication(UUID offerId, ApplicationCreateRequest applicationCreateRequest) {
        // TODO : postuler -> 201 / 400 / 409 (déjà postulé)
        throw new NotImplementedYetException("submitApplication");
    }

    @Override
    public ResponseEntity<ApplicationPagedResponse> listMyApplications(UUID studentId, ApplicationStatus status,
                                                                       Integer page, Integer size) {
        // TODO : candidatures de l'étudiant, paginées
        throw new NotImplementedYetException("listMyApplications");
    }

    @Override
    public ResponseEntity<Application> updateApplicationStatus(UUID id, ApplicationStatusUpdateRequest applicationStatusUpdateRequest) {
        JobApplication updated = review.changeStatus(id, applicationStatusUpdateRequest.getStatus(), currentCaller());
        return ResponseEntity.ok(toDto(updated));
    }

    /** La signature imposée par le SPI ne donne pas accès au JWT : on lit l'appelant dans le contexte de sécurité. */
    private static Caller currentCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) auth.getPrincipal();
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMINISTRATEUR".equals(a.getAuthority()));
        return new Caller(UUID.fromString(jwt.getSubject()), admin, jwt.getTokenValue());
    }

    private static Application toDto(JobApplication a) {
        return new Application()
                .id(a.getId())
                .offerId(a.getOffer().getId())
                .offerTitle(a.getOffer().getTitle())
                .studentId(a.getStudentId())
                .studentName(a.getStudentName())
                .cvId(a.getCvId())
                .coverLetter(a.getCoverLetter())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt());
    }
}
