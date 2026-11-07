package com.uasz.alumni.joboffer.repository;

import com.uasz.alumni.joboffer.model.Offer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, UUID> {
}
