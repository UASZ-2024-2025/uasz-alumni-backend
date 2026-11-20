package com.uasz.alumni.joboffer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.repository.JobApplicationRepository;
import com.uasz.alumni.joboffer.repository.OfferRepository;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.spi.model.JobOfferCreateRequest;
import com.uasz.alumni.joboffer.spi.model.JobOfferUpdateRequest;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import com.uasz.alumni.joboffer.spi.model.RemoteType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class OfferServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    private final OfferRepository offers = mock(OfferRepository.class);
    private final JobApplicationRepository applications = mock(JobApplicationRepository.class);
    private final CompanyClient companies = mock(CompanyClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC);
    private final OfferService service = new OfferService(offers, applications, companies, clock);

    private final UUID companyId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final AuthenticatedUser manager =
            new AuthenticatedUser(managerId, "rh@gainde.sn", "ENTREPRISE", "Awa Diop", companyId);
    private final AuthenticatedUser stranger =
            new AuthenticatedUser(UUID.randomUUID(), "x@uasz.sn", "ENTREPRISE", "Autre", UUID.randomUUID());
    private final AuthenticatedUser admin =
            new AuthenticatedUser(UUID.randomUUID(), "admin@uasz.sn", "ADMINISTRATEUR");
    private final AuthenticatedUser student =
            new AuthenticatedUser(UUID.randomUUID(), "e@uasz.sn", "ETUDIANT");

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company().id(companyId).ownerId(managerId).name("Gainde 2000").isPartner(true);
        when(companies.find(companyId)).thenReturn(Optional.of(company));
        when(offers.save(any(Offer.class))).thenAnswer(i -> i.getArgument(0));
    }

    private JobOfferCreateRequest request() {
        return new JobOfferCreateRequest()
                .title("  Développeur Java  ").type(OfferType.JOB).description("Spring Boot")
                .companyId(companyId).location("Dakar").requiredSkills(List.of("Java", " java ", "Docker", " "))
                .remoteType(RemoteType.HYBRID).durationMonths(6).salaryRange("300 000 XOF")
                .deadline(TODAY.plusDays(10));
    }

    private static ApiException statusOf(Runnable action, HttpStatus expected) {
        ApiException ex = assertThrows(ApiException.class, action::run);
        assertEquals(expected, ex.getStatus());
        return ex;
    }

    private Offer existingOffer() {
        Offer offer = new Offer("Dev", OfferType.JOB, "desc", companyId, managerId, "Dakar");
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
        return offer;
    }

    // ───── publication ─────

    @Test
    void uneEntrepriseValideePublieEtLOffreEstEnregistree() {
        Offer created = service.create(request(), manager);

        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertEquals(created, saved.getValue());
        assertEquals("Développeur Java", created.getTitle());
        assertEquals(companyId, created.getCompanyId());
        assertEquals("Gainde 2000", created.getCompanyName());
        assertEquals(managerId, created.getPublisherId());
        assertEquals("Awa Diop", created.getPublisherName());
        assertEquals(OfferStatus.OPEN, created.getStatus());
        assertEquals(List.of("Docker", "Java"), created.getRequiredSkills());
        assertEquals(TODAY.plusDays(10), created.getDeadline());
    }

    @Test
    void uneEntrepriseNonValideeNePeutPasPublier() {
        company.setIsPartner(false);

        ApiException ex = statusOf(() -> service.create(request(), manager), HttpStatus.FORBIDDEN);

        assertEquals("COMPANY_NOT_VALIDATED", ex.getCode());
        verify(offers, never()).save(any(Offer.class));
    }

    @Test
    void unCompteRattacheAUneAutreEntrepriseEstRefuse() {
        AuthenticatedUser other = new AuthenticatedUser(managerId, "rh@gainde.sn", "ENTREPRISE", "Awa", UUID.randomUUID());

        ApiException ex = statusOf(() -> service.create(request(), other), HttpStatus.FORBIDDEN);

        assertEquals("COMPANY_MISMATCH", ex.getCode());
    }

    @Test
    void unNonGestionnaireNePeutPasPublierPourCetteEntreprise() {
        AuthenticatedUser notLinked = new AuthenticatedUser(UUID.randomUUID(), "n@x.sn", "ENTREPRISE");

        assertEquals("ACCOUNT_NOT_LINKED",
                statusOf(() -> service.create(request(), notLinked), HttpStatus.FORBIDDEN).getCode());
        AuthenticatedUser alumni = new AuthenticatedUser(UUID.randomUUID(), "a@x.sn", "ALUMNI");
        assertEquals("NOT_COMPANY_MANAGER",
                statusOf(() -> service.create(request(), alumni), HttpStatus.FORBIDDEN).getCode());
    }

    @Test
    void unEtudiantNePeutPasPublier() {
        statusOf(() -> service.create(request(), student), HttpStatus.FORBIDDEN);
        verify(companies, never()).find(any());
    }

    @Test
    void unTokenSansCompanyIdMaisGestionnaireDeLaFichePeutPublier() {
        AuthenticatedUser staleToken = new AuthenticatedUser(managerId, "rh@gainde.sn", "ENTREPRISE", "Awa", null);

        assertEquals(managerId, service.create(request(), staleToken).getPublisherId());
    }

    @Test
    void lAdministrateurPublieMaisSeulementPourUneEntrepriseValidee() {
        assertEquals(admin.id(), service.create(request(), admin).getPublisherId());

        company.setIsPartner(false);
        statusOf(() -> service.create(request(), admin), HttpStatus.FORBIDDEN);
    }

    @Test
    void uneEntrepriseInconnueDonne400() {
        when(companies.find(companyId)).thenReturn(Optional.empty());

        assertEquals("COMPANY_NOT_FOUND", statusOf(() -> service.create(request(), manager), HttpStatus.BAD_REQUEST).getCode());
    }

    @Test
    void siLeServiceEntreprisesEstIndisponibleDonne503() {
        when(companies.find(companyId)).thenThrow(new RuntimeException("timeout"));

        statusOf(() -> service.create(request(), manager), HttpStatus.SERVICE_UNAVAILABLE);
        verify(offers, never()).save(any(Offer.class));
    }

    @Test
    void uneRequeteInvalideDonne400() {
        statusOf(() -> service.create(request().title(" "), manager), HttpStatus.BAD_REQUEST);
        statusOf(() -> service.create(request().requiredSkills(List.of(" ")), manager), HttpStatus.BAD_REQUEST);
        statusOf(() -> service.create(request().deadline(TODAY.minusDays(1)), manager), HttpStatus.BAD_REQUEST);
        statusOf(() -> service.create(request().durationMonths(0), manager), HttpStatus.BAD_REQUEST);
        statusOf(() -> service.create(request().type(null), manager), HttpStatus.BAD_REQUEST);
        verify(offers, never()).save(any(Offer.class));
    }

    // ───── modification ─────

    @Test
    void lAuteurModifieSonOffre() {
        Offer offer = existingOffer();

        Offer updated = service.update(offer.getId(),
                new JobOfferUpdateRequest().title("Dev Senior").location("Thiès").salaryRange("500 000 XOF"), manager);

        assertEquals("Dev Senior", updated.getTitle());
        assertEquals("Thiès", updated.getLocation());
        assertEquals("500 000 XOF", updated.getSalaryRange());
        assertEquals("desc", updated.getDescription()); // champ absent : conservé
    }

    @Test
    void unUtilisateurNiAuteurNiAdministrateurRecoit403() {
        Offer offer = existingOffer();

        ApiException ex = statusOf(() -> service.update(offer.getId(), new JobOfferUpdateRequest().title("X"), stranger),
                HttpStatus.FORBIDDEN);

        assertEquals("FORBIDDEN", ex.getCode());
        assertEquals("Dev", offer.getTitle());
    }

    @Test
    void lAdministrateurModifieUneOffreQuiNEstPasLaSienne() {
        Offer offer = existingOffer();

        assertEquals("Titre admin", service.update(offer.getId(), new JobOfferUpdateRequest().title("Titre admin"), admin).getTitle());
    }

    @Test
    void uneOffreInconnueDonne404EtUneOffreArchiveeNEstPlusModifiable() {
        statusOf(() -> service.update(UUID.randomUUID(), new JobOfferUpdateRequest(), manager), HttpStatus.NOT_FOUND);

        Offer archived = existingOffer();
        archived.changeStatus(OfferStatus.ARCHIVED);
        statusOf(() -> service.update(archived.getId(), new JobOfferUpdateRequest().title("X"), manager), HttpStatus.BAD_REQUEST);
    }

    // ───── statut ─────

    @Test
    void lAuteurClotureEtArchiveSonOffre() {
        Offer offer = existingOffer();

        assertEquals(OfferStatus.CLOSED, service.changeStatus(offer.getId(), OfferStatus.CLOSED, manager).getStatus());
        assertEquals(OfferStatus.ARCHIVED, service.changeStatus(offer.getId(), OfferStatus.ARCHIVED, manager).getStatus());
    }

    @Test
    void uneOffreArchiveeNeChangePlusDeStatut() {
        Offer offer = existingOffer();
        offer.changeStatus(OfferStatus.ARCHIVED);

        assertEquals("INVALID_STATUS_TRANSITION",
                statusOf(() -> service.changeStatus(offer.getId(), OfferStatus.OPEN, manager), HttpStatus.BAD_REQUEST).getCode());
    }

    @Test
    void rouvrirUneOffreDontLaDateLimiteEstPasseeEstRefuse() {
        Offer offer = existingOffer();
        offer.completeDetails("Gainde", "Awa", null, List.of("Java"), null, null, TODAY.minusDays(1));
        offer.changeStatus(OfferStatus.CLOSED);

        assertEquals("DEADLINE_PASSED",
                statusOf(() -> service.changeStatus(offer.getId(), OfferStatus.OPEN, manager), HttpStatus.BAD_REQUEST).getCode());
    }

    @Test
    void unNonAuteurNePeutPasChangerLeStatut() {
        Offer offer = existingOffer();

        statusOf(() -> service.changeStatus(offer.getId(), OfferStatus.CLOSED, stranger), HttpStatus.FORBIDDEN);
        assertEquals(OfferStatus.OPEN, offer.getStatus());
    }

    // ───── suppression ─────

    @Test
    void uneOffreSansCandidatureEstSupprimee() {
        Offer offer = existingOffer();
        when(applications.existsByOfferId(offer.getId())).thenReturn(false);

        service.delete(offer.getId(), manager);

        verify(offers).delete(offer);
    }

    @Test
    void uneOffreAvecCandidaturesEstArchivee() {
        Offer offer = existingOffer();
        when(applications.existsByOfferId(offer.getId())).thenReturn(true);

        service.delete(offer.getId(), manager);

        assertEquals(OfferStatus.ARCHIVED, offer.getStatus());
        verify(offers, never()).delete(any(Offer.class));
    }

    @Test
    void unNonAuteurNePeutPasSupprimer() {
        Offer offer = existingOffer();

        statusOf(() -> service.delete(offer.getId(), stranger), HttpStatus.FORBIDDEN);
        verify(offers, never()).delete(any(Offer.class));
    }

    // ───── consultation et expiration ─────

    @Test
    void uneOffreNonOuverteEstInvisibleAuxAutresMaisVisibleDeSonAuteur() {
        Offer offer = existingOffer();
        offer.changeStatus(OfferStatus.CLOSED);

        statusOf(() -> service.get(offer.getId(), null), HttpStatus.NOT_FOUND);
        statusOf(() -> service.get(offer.getId(), stranger), HttpStatus.NOT_FOUND);
        assertEquals(offer, service.get(offer.getId(), manager));
        assertEquals(offer, service.get(offer.getId(), admin));
    }

    @Test
    void uneOffreOuverteEstPublique() {
        Offer offer = existingOffer();

        assertTrue(service.get(offer.getId(), null).getStatus() == OfferStatus.OPEN);
    }

    @Test
    void laTachePlanifieeFermeLesOffresExpireesALaDateDuJour() {
        when(offers.closeExpired(TODAY)).thenReturn(3);

        assertEquals(3, service.closeExpiredOffers());
    }

    @Test
    void lesTransitionsAutoriseesSontCellesDuContrat() {
        assertTrue(OfferService.isTransitionAllowed(OfferStatus.OPEN, OfferStatus.CLOSED));
        assertTrue(OfferService.isTransitionAllowed(OfferStatus.CLOSED, OfferStatus.OPEN));
        assertTrue(OfferService.isTransitionAllowed(OfferStatus.CLOSED, OfferStatus.ARCHIVED));
        assertFalse(OfferService.isTransitionAllowed(OfferStatus.ARCHIVED, OfferStatus.OPEN));
        assertFalse(OfferService.isTransitionAllowed(OfferStatus.ARCHIVED, OfferStatus.CLOSED));
    }
}
