package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.dto.JobOfferMapper;
import com.uasz.alumni.joboffer.exceptions.NotImplementedYetException;
import com.uasz.alumni.joboffer.security.CurrentUserProvider;
import com.uasz.alumni.joboffer.service.ApplicationService;
import com.uasz.alumni.joboffer.spi.api.ApplicationsApi;
import com.uasz.alumni.joboffer.spi.model.Application;
import com.uasz.alumni.joboffer.spi.model.ApplicationCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ApplicationPagedResponse;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatusUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationsController implements ApplicationsApi {

    private final ApplicationService applications;
    private final CurrentUserProvider currentUser;

    public ApplicationsController(ApplicationService applications, CurrentUserProvider currentUser) {
        this.applications = applications;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<List<Application>> getApplicationsForOffer(UUID offerId) {
        return ResponseEntity.ok(applications.listForOffer(currentUser.require(), offerId).stream()
                .map(JobOfferMapper::toDto).toList());
    }

    @Override
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<Application> submitApplication(UUID offerId, ApplicationCreateRequest applicationCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(JobOfferMapper.toDto(
                applications.submit(currentUser.require(), offerId, applicationCreateRequest)));
    }

    @Override
    public ResponseEntity<ApplicationPagedResponse> listMyApplications(UUID studentId, ApplicationStatus status,
                                                                       Integer page, Integer size) {
        // TODO : candidatures de l'étudiant, paginées
        throw new NotImplementedYetException("listMyApplications");
    }

    @Override
    public ResponseEntity<Application> updateApplicationStatus(UUID id, ApplicationStatusUpdateRequest applicationStatusUpdateRequest) {
        return ResponseEntity.ok(JobOfferMapper.toDto(
                applications.updateStatus(currentUser.require(), id, applicationStatusUpdateRequest.getStatus())));
    }
}
