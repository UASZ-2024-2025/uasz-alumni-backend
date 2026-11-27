package com.uasz.alumni.joboffer.repository;

import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Lectures de la synthèse d'accueil entreprise (ALUM-249). Aucune écriture : interface en lecture seule. */
public interface CompanyHomeRepository extends Repository<Offer, UUID> {

    /** Offres d'une fiche entreprise dans un statut donné, les plus récentes d'abord. */
    List<Offer> findByCompanyIdAndStatusOrderByCreatedAtDesc(UUID companyId, OfferStatus status);

    /** Même chose pour un compte ENTREPRISE sans fiche rattachée : ses propres publications. */
    List<Offer> findByPublisherIdAndStatusOrderByCreatedAtDesc(UUID publisherId, OfferStatus status);

    /** Nombre de candidatures par offre, en une seule requête. Les offres sans candidature sont absentes du résultat. */
    @Query("select a.offer.id as offerId, count(a) as total from JobApplication a "
            + "where a.offer.id in :offerIds group by a.offer.id")
    List<OfferApplicationCount> countApplicationsByOffer(@Param("offerIds") Collection<UUID> offerIds);

    interface OfferApplicationCount {
        UUID getOfferId();

        long getTotal();
    }
}
