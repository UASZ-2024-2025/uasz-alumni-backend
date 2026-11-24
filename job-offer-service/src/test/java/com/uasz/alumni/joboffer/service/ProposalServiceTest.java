package com.uasz.alumni.joboffer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.model.ProposalEntity;
import com.uasz.alumni.joboffer.repository.OfferRepository;
import com.uasz.alumni.joboffer.repository.ProposalRepository;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import com.uasz.alumni.joboffer.spi.model.ProposalCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

class ProposalServiceTest {

    private final OfferRepository offers = mock(OfferRepository.class);
    private final ProposalRepository proposals = mock(ProposalRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ProposalService service = new ProposalService(offers, proposals, events, 14);

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final AuthenticatedUser recruiter = new AuthenticatedUser(recruiterId, "rec@uasz.sn", "ENTREPRISE", "Atos SN", UUID.randomUUID());
    private final AuthenticatedUser student = new AuthenticatedUser(studentId, "etu@uasz.sn", "ETUDIANT", "Awa Ndiaye", null);
    private final AuthenticatedUser otherStudent = new AuthenticatedUser(UUID.randomUUID(), "autre@uasz.sn", "ETUDIANT");
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@uasz.sn", "ADMINISTRATEUR");

    private Offer offer;

    @BeforeEach
    void setUp() {
        offer = new Offer("Développeur Java", OfferType.JOB, "desc", UUID.randomUUID(), recruiterId, "Dakar");
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
    }

    @Test
    void recruteurProposeOffreEtNotifieEtudiant() {
        ProposalCreateRequest req = new ProposalCreateRequest(offer.getId(), studentId).message("Profil idéal");

        ProposalEntity created = service.create(req, recruiter);

        assertEquals(ProposalStatus.PENDING, created.getStatus());
        assertEquals(recruiterId, created.getRecruiterId());
        assertEquals(studentId, created.getStudentId());
        assertEquals("Profil idéal", created.getMessage());

        ArgumentCaptor<ProposalCreatedEvent> captor = ArgumentCaptor.forClass(ProposalCreatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertEquals(studentId, captor.getValue().studentId());
        assertEquals("Développeur Java", captor.getValue().offerTitle());
    }

    @Test
    void etudiantNePeutPasCreerDeProposition() {
        ProposalCreateRequest req = new ProposalCreateRequest(offer.getId(), otherStudent.id());
        ApiException ex = assertThrows(ApiException.class, () -> service.create(req, student));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void autreRecruteurNePeutPasProposerLOffre() {
        AuthenticatedUser otherRecruiter = new AuthenticatedUser(UUID.randomUUID(), "other@uasz.sn", "ALUMNI");
        ProposalCreateRequest req = new ProposalCreateRequest(offer.getId(), studentId);
        ApiException ex = assertThrows(ApiException.class, () -> service.create(req, otherRecruiter));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void offreFermeeRefuseProposition() {
        offer.changeStatus(OfferStatus.CLOSED);
        ProposalCreateRequest req = new ProposalCreateRequest(offer.getId(), studentId);
        ApiException ex = assertThrows(ApiException.class, () -> service.create(req, recruiter));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void doublonPropositionPendingDonne409() {
        when(proposals.existsByOfferIdAndStudentIdAndStatus(offer.getId(), studentId, ProposalStatus.PENDING))
                .thenReturn(true);
        ProposalCreateRequest req = new ProposalCreateRequest(offer.getId(), studentId);
        ApiException ex = assertThrows(ApiException.class, () -> service.create(req, recruiter));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void seulEtudiantDestinatairePeutRepondre() {
        ProposalEntity entity = new ProposalEntity(offer, studentId, null, recruiterId, "Atos SN", null,
                OffsetDateTime.now().plusDays(7));
        when(proposals.findWithOfferById(entity.getId())).thenReturn(Optional.of(entity));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.updateStatus(entity.getId(), ProposalStatus.ACCEPTED, otherStudent));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());

        ProposalEntity updated = service.updateStatus(entity.getId(), ProposalStatus.ACCEPTED, student);
        assertEquals(ProposalStatus.ACCEPTED, updated.getStatus());
        verify(events).publishEvent(any(ProposalRespondedEvent.class));
    }

    @Test
    void propositionExpireeEstRejeteeEtMarqueeExpired() {
        ProposalEntity entity = new ProposalEntity(offer, studentId, null, recruiterId, "Atos SN", null,
                OffsetDateTime.now().minusMinutes(5));
        when(proposals.findWithOfferById(entity.getId())).thenReturn(Optional.of(entity));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.updateStatus(entity.getId(), ProposalStatus.ACCEPTED, student));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("PROPOSAL_EXPIRED", ex.getCode());
        assertEquals(ProposalStatus.EXPIRED, entity.getStatus());
    }

    @Test
    void listingFiltreSelonLeRole() {
        when(proposals.search(studentId, null, null)).thenReturn(List.of());
        when(proposals.search(null, recruiterId, ProposalStatus.ACCEPTED)).thenReturn(List.of());
        when(proposals.search(studentId, recruiterId, null)).thenReturn(List.of());

        service.list(null, null, null, student);
        verify(proposals).search(studentId, null, null);

        service.list(null, null, ProposalStatus.ACCEPTED, recruiter);
        verify(proposals).search(null, recruiterId, ProposalStatus.ACCEPTED);

        service.list(studentId, recruiterId, null, admin);
        verify(proposals).search(studentId, recruiterId, null);
    }

    @Test
    void expirationAutomatiqueDelegueAuRepository() {
        when(proposals.expirePendingProposals(any(), any(), any())).thenReturn(3);
        assertEquals(3, service.expirePendingProposals());
    }
}
