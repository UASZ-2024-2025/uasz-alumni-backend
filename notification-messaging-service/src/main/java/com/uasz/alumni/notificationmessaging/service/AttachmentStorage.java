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

    /**
     * Caractéristiques réelles du fichier stocké, lues auprès du stockage : la signature de la réponse d'envoi ne couvre
     * que {@code publicId} et {@code version}, la taille, la durée et le format envoyés par le navigateur ne sont donc
     * pas fiables. null si le fichier n'existe pas ; {@link StorageUnavailableException} si le stockage ne répond pas.
     */
    StoredFile describe(String publicId, AttachmentKind kind);

    /** Caractéristiques d'un fichier stocké (durée pour l'audio, dimensions pour une image, sinon null). */
    record StoredFile(long bytes, String format, Double durationSeconds, Integer width, Integer height) {
    }

    /** Le stockage n'a pas pu répondre (réseau, quota) : l'envoi peut être retenté. */
    class StorageUnavailableException extends RuntimeException {
        public StorageUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Lien signé vers le fichier (l'audio est converti en mp3, lisible partout). */
    String url(StoredAttachment attachment);

    /** Version réduite d'une image pour le fil ; null pour un document ou un vocal. */
    String previewUrl(StoredAttachment attachment);

    /** Efface le fichier ; une erreur est journalisée, jamais propagée. */
    void delete(StoredAttachment attachment);
}
