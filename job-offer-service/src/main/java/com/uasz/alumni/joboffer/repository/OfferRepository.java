package com.uasz.alumni.joboffer.repository;

import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, UUID>, JpaSpecificationExecutor<Offer> {

    /** Passe à CLOSED les offres OPEN dont la date limite est strictement antérieure à {@code today}. */
    @Modifying
    @Query("update Offer o set o.status = :closed where o.status = :open and o.deadline < :today")
    int closeExpired(@Param("today") LocalDate today, @Param("open") OfferStatus open,
                     @Param("closed") OfferStatus closed);

    default int closeExpired(LocalDate today) {
        return closeExpired(today, OfferStatus.OPEN, OfferStatus.CLOSED);
    }
}
