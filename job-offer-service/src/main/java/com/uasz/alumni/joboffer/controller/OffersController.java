package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.dto.JobOfferMapper;
import com.uasz.alumni.joboffer.exceptions.NotImplementedYetException;
import com.uasz.alumni.joboffer.security.CurrentUserProvider;
import com.uasz.alumni.joboffer.service.OfferService;
import com.uasz.alumni.joboffer.spi.api.OffersApi;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import com.uasz.alumni.joboffer.spi.model.JobOfferCreateRequest;
import com.uasz.alumni.joboffer.spi.model.JobOfferPagedResponse;
import com.uasz.alumni.joboffer.spi.model.JobOfferStatusUpdateRequest;
import com.uasz.alumni.joboffer.spi.model.JobOfferUpdateRequest;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import com.uasz.alumni.joboffer.spi.model.RemoteType;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OffersController implements OffersApi {

    private final OfferService offers;
    private final CurrentUserProvider currentUser;

    public OffersController(OfferService offers, CurrentUserProvider currentUser) {
        this.offers = offers;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<JobOfferPagedResponse> searchOffers(String query, OfferType type, UUID companyId, String location,
                                                              RemoteType remoteType, OfferStatus status,
                                                              Integer page, Integer size) {
        // TODO : recherche paginée (endpoint public dans le contrat)
        throw new NotImplementedYetException("searchOffers");
    }

    @Override
    @PreAuthorize("hasAnyRole('ALUMNI', 'ENSEIGNANT', 'ADMINISTRATEUR')")
    public ResponseEntity<JobOffer> createOffer(JobOfferCreateRequest jobOfferCreateRequest) {
        // Rôle ENTREPRISE à ajouter quand il existera dans le contrat user-service (architecture.md §8)
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JobOfferMapper.toDto(offers.create(currentUser.require(), jobOfferCreateRequest)));
    }

    @Override
    public ResponseEntity<JobOffer> getOfferById(UUID id) {
        return ResponseEntity.ok(JobOfferMapper.toDto(offers.get(id)));
    }

    @Override
    public ResponseEntity<JobOffer> updateOffer(UUID id, JobOfferUpdateRequest jobOfferUpdateRequest) {
        // TODO : mise à jour -> 200 / 400 / 404
        throw new NotImplementedYetException("updateOffer");
    }

    @Override
    public ResponseEntity<Void> deleteOffer(UUID id) {
        // TODO : supprimer/archiver -> 204 / 403 / 404
        throw new NotImplementedYetException("deleteOffer");
    }

    @Override
    public ResponseEntity<JobOffer> updateOfferStatus(UUID id, JobOfferStatusUpdateRequest jobOfferStatusUpdateRequest) {
        // TODO : clôturer/archiver -> 200 / 404
        throw new NotImplementedYetException("updateOfferStatus");
    }
}
