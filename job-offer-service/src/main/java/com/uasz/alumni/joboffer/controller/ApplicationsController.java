package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.exceptions.NotImplementedYetException;
import com.uasz.alumni.joboffer.spi.api.ApplicationsApi;
import com.uasz.alumni.joboffer.spi.model.Application;
import com.uasz.alumni.joboffer.spi.model.ApplicationCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ApplicationPagedResponse;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatusUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationsController implements ApplicationsApi {

    @Override
    public ResponseEntity<List<Application>> getApplicationsForOffer(UUID offerId) {
        // TODO : candidatures reçues pour l'offre -> 200 / 404
        throw new NotImplementedYetException("getApplicationsForOffer");
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
        // TODO : retenu/rejeté/en revue -> 200 / 404
        throw new NotImplementedYetException("updateApplicationStatus");
    }
}
