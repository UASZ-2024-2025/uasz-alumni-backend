package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
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
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** Implémentation de l'interface générée {@link OffersApi} : les routes viennent du contrat. */
@RestController
public class OffersController implements OffersApi {

    private final OfferService service;

    public OffersController(OfferService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<JobOfferPagedResponse> searchOffers(String query, OfferType type, UUID companyId,
                                                              String location, RemoteType remoteType,
                                                              OfferStatus status, Integer page, Integer size) {
        Page<Offer> result = service.search(
                new OfferService.Search(query, type, companyId, location, remoteType, status),
                currentUser(), page, size);
        return ResponseEntity.ok(new JobOfferPagedResponse()
                .content(result.getContent().stream().map(OffersController::toDto).toList())
                .totalElements(Math.toIntExact(result.getTotalElements()))
                .page(result.getNumber())
                .size(result.getSize()));
    }

    @Override
    public ResponseEntity<JobOffer> createOffer(JobOfferCreateRequest jobOfferCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDto(service.create(jobOfferCreateRequest, currentUser())));
    }

    @Override
    public ResponseEntity<JobOffer> getOfferById(UUID id) {
        return ResponseEntity.ok(toDto(service.get(id, currentUser())));
    }

    @Override
    public ResponseEntity<JobOffer> updateOffer(UUID id, JobOfferUpdateRequest jobOfferUpdateRequest) {
        return ResponseEntity.ok(toDto(service.update(id, jobOfferUpdateRequest, currentUser())));
    }

    @Override
    public ResponseEntity<Void> deleteOffer(UUID id) {
        service.delete(id, currentUser());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<JobOffer> updateOfferStatus(UUID id, JobOfferStatusUpdateRequest jobOfferStatusUpdateRequest) {
        return ResponseEntity.ok(toDto(service.changeStatus(id, jobOfferStatusUpdateRequest.getStatus(), currentUser())));
    }

    /**
     * La signature imposée par le SPI ne donne pas accès au JWT : on lit l'appelant dans le contexte de sécurité
     * (posé par JwtAuthenticationFilter, ALUM-28). Null pour un appel anonyme (consultation publique).
     */
    private static AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }

    private static JobOffer toDto(Offer o) {
        return new JobOffer()
                .id(o.getId())
                .title(o.getTitle())
                .type(o.getType())
                .description(o.getDescription())
                .companyId(o.getCompanyId())
                .companyName(o.getCompanyName())
                .publisherId(o.getPublisherId())
                .publisherName(o.getPublisherName())
                .location(o.getLocation())
                .remoteType(o.getRemoteType())
                .requiredSkills(o.getRequiredSkills())
                .durationMonths(o.getDurationMonths())
                .salaryRange(o.getSalaryRange())
                .deadline(o.getDeadline())
                .status(o.getStatus())
                .createdAt(o.getCreatedAt());
    }
}
