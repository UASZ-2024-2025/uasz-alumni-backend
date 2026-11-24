package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.model.StoredAttachment;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentKind;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadTicket;

/** Stockage non configuré (CLOUDINARY_URL vide) : aucune pièce jointe ; les messages déjà joints restent sans lien. */
public class DisabledAttachmentStorage implements AttachmentStorage {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public AttachmentUploadTicket ticket(String publicId, AttachmentKind kind, String allowedFormats) {
        throw new IllegalStateException("Stockage des pièces jointes non configuré");
    }

    @Override
    public boolean isAuthentic(String publicId, long version, String signature) {
        return false;
    }

    @Override
    public StoredFile describe(String publicId, AttachmentKind kind) {
        return null;
    }

    @Override
    public String url(StoredAttachment attachment) {
        return null;
    }

    @Override
    public String previewUrl(StoredAttachment attachment) {
        return null;
    }

    @Override
    public void delete(StoredAttachment attachment) {
        // rien à effacer
    }
}
