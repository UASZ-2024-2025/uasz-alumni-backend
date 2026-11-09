package com.uasz.alumni.joboffer.model;

import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Offre d'emploi ou de stage (table job_offers).
 * Seuls les champs utiles au traitement des candidatures sont mappés pour l'instant ;
 * le ticket de publication d'offre complétera le mapping (localisation, compétences, ...).
 */
@Entity
@Table(name = "job_offers")
public class Offer {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferType type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID publisherId;

    @Column(nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferStatus status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected Offer() {
    }

    public Offer(String title, OfferType type, String description, UUID companyId, UUID publisherId, String location) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.type = type;
        this.description = description;
        this.companyId = companyId;
        this.publisherId = publisherId;
        this.location = location;
        this.status = OfferStatus.OPEN;
        this.createdAt = OffsetDateTime.now();
    }

    public boolean isPublishedBy(UUID userId) {
        return publisherId.equals(userId);
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public OfferType getType() { return type; }
    public String getDescription() { return description; }
    public UUID getCompanyId() { return companyId; }
    public UUID getPublisherId() { return publisherId; }
    public String getLocation() { return location; }
    public OfferStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
