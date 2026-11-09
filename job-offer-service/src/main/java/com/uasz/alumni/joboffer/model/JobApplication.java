package com.uasz.alumni.joboffer.model;

import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Candidature d'un étudiant à une offre (table applications).
 * Nommée JobApplication pour ne pas masquer le DTO SPI Application.
 */
@Entity
@Table(name = "applications")
public class JobApplication {

    /**
     * Cycle de vie : SUBMITTED -> UNDER_REVIEW -> SHORTLISTED -> ACCEPTED / REJECTED.
     * Le rejet reste possible à chaque étape non finale ; ACCEPTED et REJECTED sont définitifs.
     */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ApplicationStatus.SUBMITTED, EnumSet.of(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.REJECTED),
            ApplicationStatus.UNDER_REVIEW, EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED),
            ApplicationStatus.SHORTLISTED, EnumSet.of(ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED),
            ApplicationStatus.ACCEPTED, EnumSet.noneOf(ApplicationStatus.class),
            ApplicationStatus.REJECTED, EnumSet.noneOf(ApplicationStatus.class));

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @Column(nullable = false)
    private UUID studentId;

    private String studentName;
    private UUID cvId;
    private String coverLetter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected JobApplication() {
    }

    public JobApplication(Offer offer, UUID studentId, String studentName, UUID cvId, String coverLetter) {
        this.id = UUID.randomUUID();
        this.offer = offer;
        this.studentId = studentId;
        this.studentName = studentName;
        this.cvId = cvId;
        this.coverLetter = coverLetter;
        this.status = ApplicationStatus.SUBMITTED;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static boolean isTransitionAllowed(ApplicationStatus from, ApplicationStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** Change le statut ; l'appelant a vérifié la transition avec {@link #isTransitionAllowed}. */
    public void changeStatus(ApplicationStatus newStatus) {
        if (!isTransitionAllowed(status, newStatus)) {
            throw new IllegalStateException("Transition interdite : " + status + " -> " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Offer getOffer() { return offer; }
    public UUID getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public UUID getCvId() { return cvId; }
    public String getCoverLetter() { return coverLetter; }
    public ApplicationStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
