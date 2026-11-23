package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.model.StoredAttachment;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentKind;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadTicket;

/**
 * Stockage des fichiers joints aux messages (ALUM-261). Le navigateur envoie le fichier directement au stockage ;
 * le service ne fait que signer l'envoi, vérifier la réponse, produire des liens signés et effacer.
 */
public interface AttachmentStorage {

    /** false tant que le stockage n'est pas configuré : les pièces jointes sont alors refusées (503). */
    boolean enabled();

    /** Autorisation d'envoyer un fichier sous {@code publicId} (formats limités à {@code allowedFormats} si non null). */
    AttachmentUploadTicket ticket(String publicId, AttachmentKind kind, String allowedFormats);

    /** Vrai si la réponse d'envoi ({@code publicId}, {@code version}, {@code signature}) vient bien du stockage. */
    boolean isAuthentic(String publicId, long version, String signature);

    /** Lien signé vers le fichier (l'audio est converti en mp3, lisible partout). */
    String url(StoredAttachment attachment);

    /** Version réduite d'une image pour le fil ; null pour un document ou un vocal. */
    String previewUrl(StoredAttachment attachment);

    /** Efface le fichier ; une erreur est journalisée, jamais propagée. */
    void delete(StoredAttachment attachment);
}
