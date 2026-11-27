package com.uasz.alumni.joboffer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.repository.CompanyHomeRepository;
import com.uasz.alumni.joboffer.repository.CompanyHomeRepository.OfferApplicationCount;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.spi.model.CompanyHomeSummary;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyHomeServiceTest {

    private final CompanyHomeRepository repository = mock(CompanyHomeRepository.class);
    private final SentProposalsCounter proposals = mock(SentProposalsCounter.class);
    private final CompanyHomeService service = new CompanyHomeService(repository, proposals);

    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final AuthenticatedUser linked = new AuthenticatedUser(userId, "e@uasz.sn", "ENTREPRISE", "Gainde", companyId);
    private final AuthenticatedUser unlinked = new AuthenticatedUser(userId, "e@uasz.sn", "ENTREPRISE");

    private final Offer java = new Offer("Développeur Java", OfferType.JOB, "Spring Boot", companyId, userId, "Dakar");
    private final Offer stage = new Offer("Stage data", OfferType.INTERNSHIP, "Pandas", companyId, userId, "Thiès");

    private static OfferApplicationCount count(UUID offerId, long total) {
        return new OfferApplicationCount() {
            @Override
            public UUID getOfferId() {
                return offerId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    @Test
    void uneEntrepriseRattacheeVoitSesOffresOuvertesAvecLeurNombreDeCandidatures() {
        when(repository.findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, OfferStatus.OPEN))
                .thenReturn(List.of(java, stage));
        when(repository.countApplicationsByOffer(eq(List.of(java.getId(), stage.getId()))))
                .thenReturn(List.of(count(java.getId(), 2)));
        when(proposals.countSentBy(userId)).thenReturn(3L);

        CompanyHomeSummary summary = service.summaryFor(linked);

        assertEquals(2, summary.getActiveOffers());
        assertEquals(2, summary.getApplicationsByOffer().size());
        assertEquals(java.getId(), summary.getApplicationsByOffer().get(0).getOfferId());
        assertEquals("Développeur Java", summary.getApplicationsByOffer().get(0).getTitle());
        assertEquals(2, summary.getApplicationsByOffer().get(0).getApplications());
        assertEquals(0, summary.getApplicationsByOffer().get(1).getApplications()); // offre sans candidature
        assertEquals(3, summary.getProposalsSent());
        verify(repository, never()).findByPublisherIdAndStatusOrderByCreatedAtDesc(userId, OfferStatus.OPEN);
    }

    @Test
    void unCompteSansEntrepriseRattacheeNeVoitQueSesPropresPublications() {
        when(repository.findByPublisherIdAndStatusOrderByCreatedAtDesc(userId, OfferStatus.OPEN))
                .thenReturn(List.of(java));
        when(repository.countApplicationsByOffer(eq(List.of(java.getId())))).thenReturn(List.of(count(java.getId(), 5)));

        CompanyHomeSummary summary = service.summaryFor(unlinked);

        assertEquals(1, summary.getActiveOffers());
        assertEquals(5, summary.getApplicationsByOffer().get(0).getApplications());
        verify(repository, never()).findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, OfferStatus.OPEN);
    }

    @Test
    void sansOffreOuverteAucunComptageDeCandidaturesNEstLance() {
        when(repository.findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, OfferStatus.OPEN))
                .thenReturn(List.of());
        when(proposals.countSentBy(userId)).thenReturn(1L);

        CompanyHomeSummary summary = service.summaryFor(linked);

        assertEquals(0, summary.getActiveOffers());
        assertTrue(summary.getApplicationsByOffer().isEmpty());
        assertEquals(1, summary.getProposalsSent());
        verify(repository, never()).countApplicationsByOffer(anyCollection());
    }
}
