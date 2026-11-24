package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.model.JobApplication;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.service.ApplicationReviewService;
import com.uasz.alumni.joboffer.service.ApplicationReviewService.Caller;
import com.uasz.alumni.joboffer.service.ApplicationSubmissionService;
import com.uasz.alumni.joboffer.spi.api.ApplicationsApi;
import com.uasz.alumni.joboffer.spi.model.Application;
import com.uasz.alumni.joboffer.spi.model.ApplicationCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ApplicationPagedResponse;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatusUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationsController implements ApplicationsApi {

    private final ApplicationReviewService review;
    private final ApplicationSubmissionService submissions;

    public ApplicationsController(ApplicationReviewService review, ApplicationSubmissionService submissions) {
        this.review = review;
        this.submissions = submissions;
    }

    @Override
    public ResponseEntity<List<Application>> getApplicationsForOffer(UUID offerId) {
        return ResponseEntity.ok(review.listForOffer(offerId, currentCaller()).stream()
                .map(ApplicationsController::toDto).toList());
    }

    @Override
    public ResponseEntity<Application> submitApplication(UUID offerId, ApplicationCreateRequest applicationCreateRequest) {
        JobApplication created = submissions.submit(offerId, applicationCreateRequest, currentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @Override
    public ResponseEntity<ApplicationPagedResponse> listMyApplications(UUID studentId, ApplicationStatus status,
                                                                       Integer page, Integer size) {
        Page<JobApplication> result = submissions.listMine(studentId, status, page, size, currentUser());
        return ResponseEntity.ok(new ApplicationPagedResponse()
                .content(result.getContent().stream().map(ApplicationsController::toDto).toList())
                .totalElements((int) Math.min(result.getTotalElements(), Integer.MAX_VALUE))
                .page(result.getNumber())
                .size(result.getSize()));
    }

    @Override
    public ResponseEntity<Application> updateApplicationStatus(UUID id, ApplicationStatusUpdateRequest applicationStatusUpdateRequest) {
        JobApplication updated = review.changeStatus(id, applicationStatusUpdateRequest.getStatus(), currentCaller());
        return ResponseEntity.ok(toDto(updated));
    }

    /**
     * La signature imposée par le SPI ne donne pas accès au JWT : on lit l'appelant dans le contexte de sécurité
     * (posé par JwtAuthenticationFilter, ALUM-28). Son jeton n'est jamais relayé à un autre service (ADR-0010).
     */
    private static AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (AuthenticatedUser) auth.getPrincipal();
    }

    private static Caller currentCaller() {
        AuthenticatedUser user = currentUser();
        boolean admin = "ADMINISTRATEUR".equals(user.role());
        return new Caller(user.id(), admin);
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
