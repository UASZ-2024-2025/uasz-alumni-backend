package com.uasz.alumni.alumnistudentcompany.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    private UUID id;
    private UUID recommenderId;
    private UUID studentId;
    private UUID companyId;
    private String comments;
    private OffsetDateTime createdAt;

    protected Recommendation() {
    }

    public Recommendation(UUID recommenderId, UUID studentId, UUID companyId, String comments) {
        this.id = UUID.randomUUID();
        this.recommenderId = recommenderId;
        this.studentId = studentId;
        this.companyId = companyId;
        this.comments = comments;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getRecommenderId() { return recommenderId; }
    public UUID getStudentId() { return studentId; }
    public UUID getCompanyId() { return companyId; }
    public String getComments() { return comments; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
