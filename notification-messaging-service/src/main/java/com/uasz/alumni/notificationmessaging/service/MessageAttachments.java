package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.ChatGroup;
import com.uasz.alumni.notificationmessaging.model.ChatGroupMember;
import com.uasz.alumni.notificationmessaging.model.StoredAttachment;
import com.uasz.alumni.notificationmessaging.repository.ChatGroupMemberRepository;
import com.uasz.alumni.notificationmessaging.repository.ChatGroupRepository;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentKind;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentSettings;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUpload;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadRequest;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadTicket;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessageType;
import com.uasz.alumni.notificationmessaging.spi.model.MessageAttachment;
import com.uasz.alumni.notificationmessaging.spi.model.SendMessageRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Stickers et pièces jointes des messages (ALUM-261), communs aux groupes et à la messagerie privée.
 *
 * <ul>
 *   <li>Autorisation d'envoi : le fichier est rangé sous {@code <dossier>/<id de l'auteur>/<uuid>} ; seul celui qui
 *       peut écrire dans la conversation l'obtient.</li>
 *   <li>À l'envoi du message, la réponse du stockage est vérifiée (signature, auteur, type, taille).</li>
 *   <li>Un message supprimé efface son fichier, après la validation de la transaction.</li>
 * </ul>
 */
@Service
public class MessageAttachments {

    static final List<String> IMAGE_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp", "heic", "heif");
    static final List<String> FILE_EXTENSIONS =
            List.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "csv", "rtf");
    static final List<String> AUDIO_EXTENSIONS = List.of("webm", "weba", "ogg", "oga", "opus", "mp3", "m4a", "mp4", "aac", "wav");
    private static final Pattern STICKER = Pattern.compile("^[a-z0-9-]{1,40}$");
    private static final int MAX_FILE_NAME = 255;
    /** Marge sur la durée d'un vocal (arrondis du navigateur et du stockage). */
    private static final double AUDIO_TOLERANCE_SECONDS = 2;

    /** Message prêt à enregistrer : type, texte (ou légende), sticker et pièce jointe. */
    public record Composed(GroupMessageType type, String content, String sticker, StoredAttachment attachment) {
    }

    /** Événement : fichier à effacer du stockage une fois la suppression du message validée. */
    public record AttachmentRemoved(StoredAttachment attachment) {
    }

    private final AttachmentStorage storage;
    private final ChatGroupRepository groups;
    private final ChatGroupMemberRepository members;
    private final ApplicationEventPublisher events;
    private final String folder;
    private final long maxImageBytes;
    private final long maxFileBytes;
    private final int maxAudioSeconds;

    public MessageAttachments(AttachmentStorage storage, ChatGroupRepository groups, ChatGroupMemberRepository members,
                              ApplicationEventPublisher events,
                              @Value("${attachments.folder:alumni-uasz/messages}") String folder,
                              @Value("${attachments.max-image-bytes:10485760}") long maxImageBytes,
                              @Value("${attachments.max-file-bytes:10485760}") long maxFileBytes,
                              @Value("${attachments.max-audio-seconds:120}") int maxAudioSeconds) {
        this.storage = storage;
        this.groups = groups;
        this.members = members;
        this.events = events;
        this.folder = folder.replaceAll("^/+|/+$", "");
        this.maxImageBytes = maxImageBytes;
        this.maxFileBytes = maxFileBytes;
        this.maxAudioSeconds = maxAudioSeconds;
    }

    public AttachmentSettings settings() {
        return new AttachmentSettings()
                .enabled(storage.enabled())
                .maxImageBytes(maxImageBytes)
                .maxFileBytes(maxFileBytes)
                .maxAudioSeconds(maxAudioSeconds)
                .fileExtensions(FILE_EXTENSIONS)
                .imageExtensions(IMAGE_EXTENSIONS);
    }

    /** Autorisation d'envoyer un fichier dans un groupe (membre pouvant écrire) ou à un interlocuteur (pas soi-même). */
    @Transactional(readOnly = true)
    public AttachmentUploadTicket ticket(AttachmentUploadRequest request, UUID caller) {
        requireEnabled();
        if (request.getKind() == null) {
            throw badRequest("INVALID_ATTACHMENT", "Le type de pièce jointe est obligatoire.");
        }
        if ((request.getGroupId() == null) == (request.getRecipientId() == null)) {
            throw badRequest("INVALID_ATTACHMENT", "Indiquez soit un groupe, soit un destinataire.");
        }
        if (request.getGroupId() != null) {
            requireCanWriteInGroup(request.getGroupId(), caller);
        } else if (request.getRecipientId().equals(caller)) {
            throw badRequest("CANNOT_MESSAGE_SELF", "Impossible de s'écrire à soi-même.");
        }
        String extension = extension(request.getFileName());
        if (!extensions(request.getKind()).contains(extension)) {
            throw badRequest("UNSUPPORTED_FILE_TYPE", "Type de fichier non accepté : " + (extension.isEmpty() ? "sans extension" : "." + extension));
        }
        String publicId = folder + "/" + caller + "/" + UUID.randomUUID()
                + (request.getKind() == AttachmentKind.FILE ? "." + extension : "");
        String allowedFormats = switch (request.getKind()) {
            case IMAGE -> String.join(",", IMAGE_EXTENSIONS);
            case AUDIO -> String.join(",", AUDIO_EXTENSIONS);
            case FILE -> null; // fichiers bruts : pas de détection de format côté stockage, l'extension est contrôlée ici
        };
        return storage.ticket(publicId, request.getKind(), allowedFormats);
    }

    /**
     * Valide le contenu d'un message : texte seul ({@code validText}), sticker seul, ou pièce jointe avec une légende
     * facultative (même règle de longueur que le texte).
     */
    public Composed compose(SendMessageRequest request, UUID caller, UnaryOperator<String> validText) {
        String content = request.getContent();
        boolean hasText = content != null && !content.isBlank();
        if (request.getSticker() != null && request.getAttachment() != null) {
            throw badRequest("INVALID_MESSAGE", "Un message contient soit un sticker, soit une pièce jointe.");
        }
        if (request.getSticker() != null) {
            if (hasText) {
                throw badRequest("INVALID_MESSAGE", "Un sticker s'envoie sans texte.");
            }
            if (!STICKER.matcher(request.getSticker()).matches()) {
                throw badRequest("INVALID_STICKER", "Sticker inconnu.");
            }
            return new Composed(GroupMessageType.STICKER, null, request.getSticker(), null);
        }
        if (request.getAttachment() != null) {
            StoredAttachment attachment = verify(request.getAttachment(), caller);
            return new Composed(GroupMessageType.ATTACHMENT, hasText ? validText.apply(content) : null, null, attachment);
        }
        return new Composed(GroupMessageType.TEXT, validText.apply(content), null, null);
    }

    /** Vue d'une pièce jointe avec ses liens signés ; null sans pièce jointe. */
    public MessageAttachment view(StoredAttachment a) {
        if (a == null) {
            return null;
        }
        return new MessageAttachment()
                .kind(a.getKind())
                .fileName(a.getFileName())
                .format(a.getFormat())
                .sizeBytes(a.getSizeBytes())
                .durationSeconds(a.getDurationSeconds())
                .width(a.getWidth())
                .height(a.getHeight())
                .url(storage.url(a))
                .previewUrl(storage.previewUrl(a));
    }

    /** À appeler quand un message est supprimé : le fichier est effacé une fois la suppression validée. */
    public void discard(StoredAttachment attachment) {
        if (attachment != null) {
            events.publishEvent(new AttachmentRemoved(attachment));
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onRemoved(AttachmentRemoved event) {
        // hors du fil de la requête : un stockage lent ne retarde pas la réponse
        CompletableFuture.runAsync(() -> storage.delete(event.attachment()));
    }

    // ───── Outils ─────

    private StoredAttachment verify(AttachmentUpload upload, UUID caller) {
        requireEnabled();
        AttachmentKind kind = upload.getKind();
        String publicId = upload.getPublicId();
        if (kind == null || publicId == null || upload.getVersion() == null
                || !publicId.startsWith(folder + "/" + caller + "/")
                || !storage.isAuthentic(publicId, upload.getVersion(), upload.getSignature())) {
            throw badRequest("INVALID_ATTACHMENT", "Pièce jointe invalide : renvoyez le fichier.");
        }
        String fileName = fileName(upload.getFileName());
        String format = upload.getFormat() == null ? null : upload.getFormat().toLowerCase(Locale.ROOT);
        long size = upload.getSizeBytes() == null ? -1 : upload.getSizeBytes();
        if (size < 0) {
            throw badRequest("INVALID_ATTACHMENT", "Taille de la pièce jointe manquante.");
        }
        switch (kind) {
            case IMAGE -> {
                if (format == null || !IMAGE_EXTENSIONS.contains(format)) {
                    throw badRequest("UNSUPPORTED_FILE_TYPE", "Format d'image non accepté.");
                }
                requireSize(size, maxImageBytes);
            }
            case FILE -> {
                format = extension(publicId);
                if (!FILE_EXTENSIONS.contains(format)) {
                    throw badRequest("UNSUPPORTED_FILE_TYPE", "Type de document non accepté.");
                }
                requireSize(size, maxFileBytes);
            }
            case AUDIO -> {
                Double duration = upload.getDurationSeconds();
                if (duration != null && duration > maxAudioSeconds + AUDIO_TOLERANCE_SECONDS) {
                    throw badRequest("ATTACHMENT_TOO_LARGE",
                            "Message vocal trop long (" + maxAudioSeconds + " secondes au maximum).");
                }
                requireSize(size, maxFileBytes);
            }
        }
        return new StoredAttachment(kind, publicId, upload.getVersion(), format, fileName, size,
                kind == AttachmentKind.AUDIO ? upload.getDurationSeconds() : null,
                kind == AttachmentKind.IMAGE ? upload.getWidth() : null,
                kind == AttachmentKind.IMAGE ? upload.getHeight() : null);
    }

    private void requireCanWriteInGroup(UUID groupId, UUID caller) {
        ChatGroup g = groups.findById(groupId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Groupe introuvable : " + groupId));
        ChatGroupMember me = members.findByGroupIdAndUserId(groupId, caller).orElseThrow(() ->
                new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Vous n'êtes pas membre de ce groupe."));
        if (g.isOnlyAdminsCanSend() && !me.isGroupAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Seuls les administrateurs peuvent envoyer des messages dans ce groupe.");
        }
    }

    private void requireEnabled() {
        if (!storage.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ATTACHMENTS_DISABLED",
                    "Les pièces jointes ne sont pas encore activées sur la plateforme.");
        }
    }

    private void requireSize(long size, long max) {
        if (size > max) {
            throw badRequest("ATTACHMENT_TOO_LARGE", "Fichier trop volumineux (" + (max / (1024 * 1024)) + " Mo au maximum).");
        }
    }

    private static List<String> extensions(AttachmentKind kind) {
        return switch (kind) {
            case IMAGE -> IMAGE_EXTENSIONS;
            case FILE -> FILE_EXTENSIONS;
            case AUDIO -> AUDIO_EXTENSIONS;
        };
    }

    static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Nom affiché : sans chemin ni caractère de contrôle, 255 caractères au plus. */
    static String fileName(String raw) {
        String name = raw == null ? "" : raw.replaceAll(".*[/\\\\]", "").replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            throw badRequest("INVALID_ATTACHMENT", "Nom de fichier manquant.");
        }
        return name.length() > MAX_FILE_NAME ? name.substring(name.length() - MAX_FILE_NAME) : name;
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
