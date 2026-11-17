package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.exceptions.NotImplementedYetException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OffersController implements OffersApi {

    @Override
    public ResponseEntity<JobOfferPagedResponse> searchOffers(String query, OfferType type, UUID companyId, String location,
                                                              RemoteType remoteType, OfferStatus status,
                                                              Integer page, Integer size) {
        // TODO : recherche paginée (endpoint public dans le contrat)
        throw new NotImplementedYetException("searchOffers");
    }

    @Override
    public ResponseEntity<JobOffer> createOffer(JobOfferCreateRequest jobOfferCreateRequest) {
        // TODO : publier une offre (publisherId = utilisateur courant) -> 201 / 400 / 403
        throw new NotImplementedYetException("createOffer");
    }

    @Override
    public ResponseEntity<JobOffer> getOfferById(UUID id) {
        // TODO : détails (endpoint public) -> 200 / 404
        throw new NotImplementedYetException("getOfferById");
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
