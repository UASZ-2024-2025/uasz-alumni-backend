package com.uasz.alumni.alumnistudentcompany.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Promotion académique (ALUM-88) : une année et une spécialité. Ses diplômés ne sont pas stockés : ce sont les
 * Alumni de même année de diplôme et de même spécialité.
 */
@Entity
@Table(name = "promotions")
public class PromotionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year", nullable = false, updatable = false)
    private Integer academicYear;

    @Column(nullable = false, updatable = false, length = 150)
    private String specialty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PromotionEntity() {
    }

    public PromotionEntity(Integer academicYear, String specialty) {
        this.academicYear = academicYear;
        this.specialty = specialty;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public Integer getAcademicYear() { return academicYear; }
    public String getSpecialty() { return specialty; }
    public Instant getCreatedAt() { return createdAt; }
}
