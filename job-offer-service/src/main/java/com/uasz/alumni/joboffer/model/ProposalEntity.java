package com.uasz.alumni.joboffer.model;

import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
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
import java.util.Set;
import java.util.UUID;

/**
 * Proposition directe d'une offre à un étudiant (table {@code proposals}, ALUM-174).
 * Nommée {@code ProposalEntity} pour ne pas masquer le DTO SPI {@code Proposal}.
 */
@Entity
@Table(name = "proposals")
public class ProposalEntity {

    private static final Set<ProposalStatus> ALLOWED_STUDENT_TARGETS =
            EnumSet.of(ProposalStatus.ACCEPTED, ProposalStatus.REJECTED);

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @Column(nullable = false)
    private UUID studentId;

    private String studentName;

    @Column(nullable = false)
    private UUID recruiterId;

    private String recruiterName;

    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected ProposalEntity() {
    }

    public ProposalEntity(Offer offer, UUID studentId, String studentName, UUID recruiterId,
                          String recruiterName, String message, OffsetDateTime expiresAt) {
        this.id = UUID.randomUUID();
        this.offer = offer;
        this.studentId = studentId;
        this.studentName = studentName;
        this.recruiterId = recruiterId;
        this.recruiterName = recruiterName;
        this.message = message;
        this.status = ProposalStatus.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public boolean isExpiredAt(OffsetDateTime now) {
        return status == ProposalStatus.PENDING && !expiresAt.isAfter(now);
    }

    public static boolean isStudentResponseAllowed(ProposalStatus current, ProposalStatus target) {
        return current == ProposalStatus.PENDING && ALLOWED_STUDENT_TARGETS.contains(target);
    }

    public void respond(ProposalStatus newStatus) {
        if (!isStudentResponseAllowed(this.status, newStatus)) {
            throw new IllegalStateException("Transition interdite : " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markExpired() {
        if (this.status == ProposalStatus.PENDING) {
            this.status = ProposalStatus.EXPIRED;
            this.updatedAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public Offer getOffer() { return offer; }
    public UUID getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public UUID getRecruiterId() { return recruiterId; }
    public String getRecruiterName() { return recruiterName; }
    public String getMessage() { return message; }
    public ProposalStatus getStatus() { return status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
