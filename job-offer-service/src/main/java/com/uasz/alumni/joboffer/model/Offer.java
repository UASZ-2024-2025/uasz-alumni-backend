package com.uasz.alumni.joboffer.model;

import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import com.uasz.alumni.joboffer.spi.model.RemoteType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * Offre d'emploi ou de stage (table job_offers, colonnes du schéma JobOffer du contrat).
 * Utilisée par le traitement des candidatures (ALUM-173) et par la publication / gestion des offres (ALUM-148).
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

    /** Nom de l'entreprise au moment de la publication (copie : la fiche vit dans un autre service). */
    private String companyName;

    @Column(nullable = false)
    private UUID publisherId;

    private String publisherName;

    @Column(nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    private RemoteType remoteType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_offer_required_skills", joinColumns = @JoinColumn(name = "offer_id"))
    @Column(name = "skill", nullable = false, length = 100)
    @BatchSize(size = 50)
    private Set<String> requiredSkills = new LinkedHashSet<>();

    private Integer durationMonths;

    private String salaryRange;

    private LocalDate deadline;

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

    /** Renseigne les informations facultatives et dénormalisées à la publication. */
    public void completeDetails(String companyName, String publisherName, RemoteType remoteType,
                                Collection<String> requiredSkills, Integer durationMonths, String salaryRange,
                                LocalDate deadline) {
        this.companyName = companyName;
        this.publisherName = publisherName;
        this.remoteType = remoteType;
        replaceSkills(requiredSkills);
        this.durationMonths = durationMonths;
        this.salaryRange = salaryRange;
        this.deadline = deadline;
    }

    /** Mise à jour partielle : un argument null laisse la valeur inchangée ; une rémunération vide l'efface. */
    public void update(String title, OfferType type, String description, String location, RemoteType remoteType,
                       Collection<String> requiredSkills, Integer durationMonths, String salaryRange,
                       LocalDate deadline) {
        if (title != null) {
            this.title = title;
        }
        if (type != null) {
            this.type = type;
        }
        if (description != null) {
            this.description = description;
        }
        if (location != null) {
            this.location = location;
        }
        if (remoteType != null) {
            this.remoteType = remoteType;
        }
        if (requiredSkills != null && !requiredSkills.isEmpty()) {
            replaceSkills(requiredSkills);
        }
        if (durationMonths != null) {
            this.durationMonths = durationMonths;
        }
        if (salaryRange != null) {
            this.salaryRange = salaryRange.isEmpty() ? null : salaryRange;
        }
        if (deadline != null) {
            this.deadline = deadline;
        }
    }

    public void changeStatus(OfferStatus newStatus) {
        this.status = newStatus;
    }

    private void replaceSkills(Collection<String> skills) {
        this.requiredSkills.clear();
        if (skills != null) {
            this.requiredSkills.addAll(skills);
        }
    }

    public boolean isPublishedBy(UUID userId) {
        return publisherId.equals(userId);
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public OfferType getType() { return type; }
    public String getDescription() { return description; }
    public UUID getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public UUID getPublisherId() { return publisherId; }
    public String getPublisherName() { return publisherName; }
    public String getLocation() { return location; }
    public RemoteType getRemoteType() { return remoteType; }
    /** Compétences triées (l'ordre de saisie n'est pas conservé : la table a pour clé (offre, compétence)). */
    public List<String> getRequiredSkills() {
        return requiredSkills.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    public Integer getDurationMonths() { return durationMonths; }
    public String getSalaryRange() { return salaryRange; }
    public LocalDate getDeadline() { return deadline; }
    public OfferStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
