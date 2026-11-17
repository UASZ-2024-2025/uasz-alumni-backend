package com.uasz.alumni.alumnistudentcompany.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Profil Alumni (table alumni_profiles). Nommé Alumni pour ne pas masquer le DTO SPI AlumniProfile. */
@Entity
@Table(name = "alumni_profiles")
public class Alumni {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    private String firstName;
    private String lastName;
    private String email;
    private Integer graduationYear;
    private String specialty;
    private String currentCompany;
    private String currentPosition;
    private String linkedinUrl;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected Alumni() {
    }

    public Alumni(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** Le poste actuel du profil reflète toujours l'expérience marquée isCurrent. */
    public void updateCurrentJob(String company, String position) {
        this.currentCompany = company;
        this.currentPosition = position;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public Integer getGraduationYear() { return graduationYear; }
    public String getSpecialty() { return specialty; }
    public String getCurrentCompany() { return currentCompany; }
    public String getCurrentPosition() { return currentPosition; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
