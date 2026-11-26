package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.repository.CompanyHomeRepository;
import com.uasz.alumni.joboffer.repository.CompanyHomeRepository.OfferApplicationCount;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.spi.model.CompanyHomeSummary;
import com.uasz.alumni.joboffer.spi.model.OfferApplicationsCount;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synthèse de la page d'accueil d'une entreprise (ALUM-249) : offres ouvertes, candidatures reçues par offre et
 * propositions envoyées, calculées en quelques requêtes à partir des données du service.
 * Le rôle ENTREPRISE est exigé par le contrôleur ; ici, seules les données de l'appelant sont lues.
 */
@Service
public class CompanyHomeService {

    private final CompanyHomeRepository repository;
    private final SentProposalsCounter proposals;

    public CompanyHomeService(CompanyHomeRepository repository, SentProposalsCounter proposals) {
        this.repository = repository;
        this.proposals = proposals;
    }

    @Transactional(readOnly = true)
    public CompanyHomeSummary summaryFor(AuthenticatedUser caller) {
        List<Offer> open = caller.companyId() != null
                ? repository.findByCompanyIdAndStatusOrderByCreatedAtDesc(caller.companyId(), OfferStatus.OPEN)
                : repository.findByPublisherIdAndStatusOrderByCreatedAtDesc(caller.id(), OfferStatus.OPEN);

        Map<UUID, Long> applicationsByOffer = open.isEmpty()
                ? Map.of()
                : repository.countApplicationsByOffer(open.stream().map(Offer::getId).toList()).stream()
                        .collect(Collectors.toMap(OfferApplicationCount::getOfferId, OfferApplicationCount::getTotal));

        List<OfferApplicationsCount> lines = open.stream().map(offer -> {
            OfferApplicationsCount line = new OfferApplicationsCount();
            line.setOfferId(offer.getId());
            line.setTitle(offer.getTitle());
            line.setApplications(Math.toIntExact(applicationsByOffer.getOrDefault(offer.getId(), 0L)));
            return line;
        }).toList();

        CompanyHomeSummary summary = new CompanyHomeSummary();
        summary.setActiveOffers(open.size());
        summary.setApplicationsByOffer(lines);
        summary.setProposalsSent(Math.toIntExact(proposals.countSentBy(caller.id())));
        return summary;
    }
}
