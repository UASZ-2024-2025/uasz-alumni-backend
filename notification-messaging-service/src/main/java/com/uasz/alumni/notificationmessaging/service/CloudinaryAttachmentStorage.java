package com.uasz.alumni.notificationmessaging.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.uasz.alumni.notificationmessaging.model.StoredAttachment;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentKind;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadTicket;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stockage Cloudinary. Les fichiers sont envoyés en type {@code authenticated} : ils ne sont lisibles qu'avec un lien
 * signé par le service, remis aux seuls participants de la conversation.
 */
public class CloudinaryAttachmentStorage implements AttachmentStorage {

    static final String TYPE = "authenticated";
    private static final Logger log = LoggerFactory.getLogger(CloudinaryAttachmentStorage.class);

    private final Cloudinary cloudinary;
    private final Clock clock;

    public CloudinaryAttachmentStorage(String cloudinaryUrl, Clock clock) {
        this.cloudinary = new Cloudinary(cloudinaryUrl);
        this.cloudinary.config.secure = true;
        this.cloudinary.config.analytics = false; // pas de paramètre de suivi « ?_a= » dans les liens
        this.clock = clock;
        if (cloudinary.config.cloudName == null || cloudinary.config.apiKey == null || cloudinary.config.apiSecret == null) {
            throw new IllegalStateException("CLOUDINARY_URL invalide (attendu : cloudinary://<api_key>:<api_secret>@<cloud_name>)");
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public AttachmentUploadTicket ticket(String publicId, AttachmentKind kind, String allowedFormats) {
        long timestamp = clock.instant().getEpochSecond();
        Map<String, Object> params = new TreeMap<>();
        params.put("public_id", publicId);
        params.put("timestamp", timestamp);
        params.put("type", TYPE);
        if (allowedFormats != null) {
            params.put("allowed_formats", allowedFormats);
        }
        String signature = cloudinary.apiSignRequest(params, cloudinary.config.apiSecret, cloudinary.config.signatureVersion);
        return new AttachmentUploadTicket()
                .uploadUrl("https://api.cloudinary.com/v1_1/" + cloudinary.config.cloudName + "/" + resourceType(kind) + "/upload")
                .apiKey(cloudinary.config.apiKey)
                .timestamp(timestamp)
                .signature(signature)
                .publicId(publicId)
                .type(TYPE)
                .allowedFormats(allowedFormats);
    }

    @Override
    public boolean isAuthentic(String publicId, long version, String signature) {
        return signature != null && cloudinary.verifyApiResponseSignature(publicId, String.valueOf(version), signature);
    }

    @Override
    public String url(StoredAttachment a) {
        var url = cloudinary.url().resourceType(resourceType(a.getKind())).type(TYPE).signed(true).version(a.getVersion());
        return switch (a.getKind()) {
            // HEIC (photos d'iPhone) : servi en jpg, que tous les navigateurs affichent
            case IMAGE -> url.format(List.of("heic", "heif").contains(a.getFormat()) ? "jpg" : a.getFormat()).generate(a.getPublicId());
            case AUDIO -> url.format("mp3").generate(a.getPublicId());
            // document : le public_id porte déjà l'extension
            case FILE -> url.generate(a.getPublicId());
        };
    }

    @Override
    @SuppressWarnings("rawtypes")
    public String previewUrl(StoredAttachment a) {
        if (a.getKind() != AttachmentKind.IMAGE) {
            return null;
        }
        return cloudinary.url().resourceType("image").type(TYPE).signed(true).version(a.getVersion())
                .transformation(new Transformation().rawTransformation("c_limit,w_640,h_640,q_auto"))
                .format("jpg")
                .generate(a.getPublicId());
    }

    @Override
    public void delete(StoredAttachment a) {
        try {
            cloudinary.uploader().destroy(a.getPublicId(),
                    Map.of("resource_type", resourceType(a.getKind()), "type", TYPE, "invalidate", true));
        } catch (Exception e) {
            log.warn("Pièce jointe {} non effacée du stockage : {}", a.getPublicId(), e.getMessage());
        }
    }

    static String resourceType(AttachmentKind kind) {
        return switch (kind) {
            case IMAGE -> "image";
            case AUDIO -> "video"; // Cloudinary range l'audio avec la vidéo (conversion mp3 à la volée)
            case FILE -> "raw";
        };
    }
}
