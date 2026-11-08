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
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_offers")
public class JobOfferEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OfferType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "publisher_id", nullable = false)
    private UUID publisherId;

    @Column(name = "publisher_name", length = 200)
    private String publisherName;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "remote_type", length = 20)
    private RemoteType remoteType;

    @ElementCollection
    @CollectionTable(name = "job_offer_required_skills", joinColumns = @JoinColumn(name = "offer_id"))
    @OrderColumn(name = "position")
    @Column(name = "skill", nullable = false, length = 100)
    private List<String> requiredSkills = new ArrayList<>();

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "salary_range", length = 100)
    private String salaryRange;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobOfferEntity() {
    }

    public JobOfferEntity(UUID publisherId, String title, OfferType type, String description, UUID companyId) {
        this.id = UUID.randomUUID();
        this.publisherId = publisherId;
        this.title = title;
        this.type = type;
        this.description = description;
        this.companyId = companyId;
        this.status = OfferStatus.OPEN;
        this.createdAt = Instant.now();
    }

    /** Une offre accepte des candidatures si elle est ouverte et que sa date limite n'est pas dépassée. */
    public boolean acceptsApplications(LocalDate today) {
        return status == OfferStatus.OPEN && (deadline == null || !today.isAfter(deadline));
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
    public List<String> getRequiredSkills() { return requiredSkills; }
    public Integer getDurationMonths() { return durationMonths; }
    public String getSalaryRange() { return salaryRange; }
    public LocalDate getDeadline() { return deadline; }
    public OfferStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setLocation(String location) { this.location = location; }
    public void setRemoteType(RemoteType remoteType) { this.remoteType = remoteType; }
    public void setRequiredSkills(List<String> skills) {
        this.requiredSkills.clear();
        if (skills != null) {
            this.requiredSkills.addAll(skills);
        }
    }
    public void setDurationMonths(Integer durationMonths) { this.durationMonths = durationMonths; }
    public void setSalaryRange(String salaryRange) { this.salaryRange = salaryRange; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public void setStatus(OfferStatus status) { this.status = status; }
}
