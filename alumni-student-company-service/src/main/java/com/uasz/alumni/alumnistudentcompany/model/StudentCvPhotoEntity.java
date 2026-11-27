package com.uasz.alumni.alumnistudentcompany.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Photo du CV d'un étudiant (ALUM-255) : une par profil, identifiée par l'id du profil étudiant.
 * Table séparée du CV : la photo existe même avant le premier enregistrement du CV, et le CV se lit sans la charger.
 */
@Entity
@Table(name = "student_cv_photos")
public class StudentCvPhotoEntity {

    /** Taille maximale d'une photo : 2 Mo. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    // Type fixé à BYTEA : sans lui, Hibernate choisit BLOB pour un tableau de 2 Mo, que la base H2 des tests
    // (mode PostgreSQL) refuse. BYTEA est le type de la migration V12 et est accepté par H2.
    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentCvPhotoEntity() {
    }

    public StudentCvPhotoEntity(UUID studentId) {
        this.studentId = studentId;
    }

    /** Remplace l'image (contenu déjà validé par le service). */
    public void replace(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = Instant.now();
    }

    public UUID getStudentId() { return studentId; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public Instant getUpdatedAt() { return updatedAt; }
}
