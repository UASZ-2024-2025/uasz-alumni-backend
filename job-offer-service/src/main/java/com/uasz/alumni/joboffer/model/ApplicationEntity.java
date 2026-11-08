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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "applications")
public class ApplicationEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private JobOfferEntity offer;

    /** Profil étudiant (alumni-student-company-service). */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** Compte de l'étudiant (user-service), destinataire des notifications. */
    @Column(name = "student_user_id", nullable = false)
    private UUID studentUserId;

    @Column(name = "student_name", length = 200)
    private String studentName;

    @Column(name = "cv_id")
    private UUID cvId;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApplicationEntity() {
    }

    public ApplicationEntity(JobOfferEntity offer, UUID studentId, UUID studentUserId, UUID cvId, String coverLetter) {
        this.id = UUID.randomUUID();
        this.offer = offer;
        this.studentId = studentId;
        this.studentUserId = studentUserId;
        this.cvId = cvId;
        this.coverLetter = coverLetter;
        this.status = ApplicationStatus.SUBMITTED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Change le statut ; renvoie {@code false} si le statut était déjà celui demandé. */
    public boolean changeStatus(ApplicationStatus newStatus) {
        if (this.status == newStatus) {
            return false;
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
        return true;
    }

    public UUID getId() { return id; }
    public JobOfferEntity getOffer() { return offer; }
    public UUID getStudentId() { return studentId; }
    public UUID getStudentUserId() { return studentUserId; }
    public String getStudentName() { return studentName; }
    public UUID getCvId() { return cvId; }
    public String getCoverLetter() { return coverLetter; }
    public ApplicationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
