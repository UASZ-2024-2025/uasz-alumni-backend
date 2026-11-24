package com.uasz.alumni.notificationmessaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Annuaire local (table chat_users) : nom, rôle et e-mail recopiés du jeton de l'utilisateur (écrits par upsert). */
@Entity
@Table(name = "chat_users")
public class ChatUser {

    @Id
    private UUID userId;

    private String displayName;

    private String appRole;

    /** Destinataire des notifications du canal EMAIL (ALUM-135). */
    private String email;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected ChatUser() {
    }

    public UUID getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getAppRole() { return appRole; }
    public String getEmail() { return email; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
