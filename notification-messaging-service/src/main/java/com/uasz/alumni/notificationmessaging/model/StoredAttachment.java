package com.uasz.alumni.notificationmessaging.model;

import com.uasz.alumni.notificationmessaging.spi.model.AttachmentKind;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Pièce jointe d'un message (ALUM-261) : le fichier est chez Cloudinary, la base garde de quoi le retrouver
 * ({@code publicId}, {@code version}) et l'afficher (nom, taille, durée, dimensions).
 */
@Embeddable
public class StoredAttachment {

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_kind", length = 10)
    private AttachmentKind kind;

    @Column(name = "attachment_public_id", length = 255)
    private String publicId;

    @Column(name = "attachment_version")
    private Long version;

    @Column(name = "attachment_format", length = 20)
    private String format;

    @Column(name = "attachment_file_name", length = 255)
    private String fileName;

    @Column(name = "attachment_size_bytes")
    private Long sizeBytes;

    @Column(name = "attachment_duration_seconds")
    private Double durationSeconds;

    @Column(name = "attachment_width")
    private Integer width;

    @Column(name = "attachment_height")
    private Integer height;

    protected StoredAttachment() {
    }

    public StoredAttachment(AttachmentKind kind, String publicId, long version, String format, String fileName,
                            long sizeBytes, Double durationSeconds, Integer width, Integer height) {
        this.kind = kind;
        this.publicId = publicId;
        this.version = version;
        this.format = format;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.durationSeconds = durationSeconds;
        this.width = width;
        this.height = height;
    }

    public AttachmentKind getKind() { return kind; }
    public String getPublicId() { return publicId; }
    public Long getVersion() { return version; }
    public String getFormat() { return format; }
    public String getFileName() { return fileName; }
    public Long getSizeBytes() { return sizeBytes; }
    public Double getDurationSeconds() { return durationSeconds; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
}
