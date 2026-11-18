package com.uasz.alumni.alumnistudentcompany.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Expérience professionnelle d'un Alumni (table work_experiences). */
@Entity
@Table(name = "work_experiences")
public class Experience {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID alumniId;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String position;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    private String description;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected Experience() {
    }

    public Experience(UUID alumniId, String companyName, String position, LocalDate startDate,
                      LocalDate endDate, boolean current, String description) {
        this.id = UUID.randomUUID();
        this.alumniId = alumniId;
        this.companyName = companyName;
        this.position = position;
        this.startDate = startDate;
        this.endDate = endDate;
        this.current = current;
        this.description = description;
        this.createdAt = OffsetDateTime.now();
    }

    /** Retire le statut courant lorsqu'une nouvelle expérience courante est ajoutée. */
    public void unmarkCurrent() {
        this.current = false;
    }

    public UUID getId() { return id; }
    public UUID getAlumniId() { return alumniId; }
    public String getCompanyName() { return companyName; }
    public String getPosition() { return position; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isCurrent() { return current; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
