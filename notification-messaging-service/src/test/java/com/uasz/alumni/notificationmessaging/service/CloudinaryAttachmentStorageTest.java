package com.uasz.alumni.notificationmessaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.notificationmessaging.model.StoredAttachment;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentKind;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadTicket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * Signatures Cloudinary (ALUM-261), comparées à l'algorithme documenté : SHA-1 hexadécimal des paramètres triés
 * « clé=valeur » joints par « & », suivis du secret.
 */
class CloudinaryAttachmentStorageTest {

    private static final String SECRET = "secret-de-test";
    private static final Instant MAINTENANT = Instant.parse("2026-11-23T10:00:00Z");
    private final CloudinaryAttachmentStorage storage = new CloudinaryAttachmentStorage(
            "cloudinary://123456:" + SECRET + "@demo-uasz", Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private static String sha1(String texte) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(texte.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void lAutorisationDEnvoiEstSigneeCommeLAttendCloudinary() throws Exception {
        AttachmentUploadTicket t = storage.ticket("alumni/u1/abc", AttachmentKind.IMAGE, "jpg,png");

        long ts = MAINTENANT.getEpochSecond();
        assertThat(t.getTimestamp()).isEqualTo(ts);
        assertThat(t.getApiKey()).isEqualTo("123456");
        assertThat(t.getType()).isEqualTo("authenticated");
        assertThat(t.getUploadUrl()).isEqualTo("https://api.cloudinary.com/v1_1/demo-uasz/image/upload");
        assertThat(t.getSignature()).isEqualTo(
                sha1("allowed_formats=jpg,png&public_id=alumni/u1/abc&timestamp=" + ts + "&type=authenticated" + SECRET));
    }

    @Test
    void unDocumentEtUnVocalVontVersLeBonTypeDeRessource() throws Exception {
        assertThat(storage.ticket("alumni/u1/doc.pdf", AttachmentKind.FILE, null).getUploadUrl()).endsWith("/raw/upload");
        AttachmentUploadTicket vocal = storage.ticket("alumni/u1/v", AttachmentKind.AUDIO, null);
        assertThat(vocal.getUploadUrl()).endsWith("/video/upload");
        assertThat(vocal.getSignature()).isEqualTo(
                sha1("public_id=alumni/u1/v&timestamp=" + MAINTENANT.getEpochSecond() + "&type=authenticated" + SECRET));
    }

    @Test
    void seuleUneReponseSigneeParCloudinaryEstAuthentique() throws Exception {
        String signature = sha1("public_id=alumni/u1/abc&version=1700000000" + SECRET);
        assertThat(storage.isAuthentic("alumni/u1/abc", 1700000000L, signature)).isTrue();
        assertThat(storage.isAuthentic("alumni/u1/autre", 1700000000L, signature)).isFalse();
        assertThat(storage.isAuthentic("alumni/u1/abc", 1700000001L, signature)).isFalse();
        assertThat(storage.isAuthentic("alumni/u1/abc", 1700000000L, null)).isFalse();
    }

    @Test
    void lesLiensSontSignesEtAuthentifies() {
        StoredAttachment image = new StoredAttachment(AttachmentKind.IMAGE, "alumni/u1/photo", 42, "heic", "p.heic", 10, null, 10, 10);
        assertThat(storage.url(image))
                .startsWith("https://res.cloudinary.com/demo-uasz/image/authenticated/s--")
                .endsWith("/v42/alumni/u1/photo.jpg");
        assertThat(storage.previewUrl(image)).contains("/c_limit,w_640,h_640,q_auto/").endsWith("/v42/alumni/u1/photo.jpg");

        StoredAttachment vocal = new StoredAttachment(AttachmentKind.AUDIO, "alumni/u1/vocal", 3, "webm", "v.webm", 10, 4.0, null, null);
        assertThat(storage.url(vocal)).contains("/video/authenticated/s--").endsWith("/v3/alumni/u1/vocal.mp3");
        assertThat(storage.previewUrl(vocal)).isNull();

        StoredAttachment doc = new StoredAttachment(AttachmentKind.FILE, "alumni/u1/cours.pdf", 5, "pdf", "cours.pdf", 10, null, null, null);
        assertThat(storage.url(doc)).contains("/raw/authenticated/s--").endsWith("/v5/alumni/u1/cours.pdf");
    }

    @Test
    void uneUrlIncompleteEstRefuseeAuDemarrage() {
        assertThatThrownBy(() -> new CloudinaryAttachmentStorage("cloudinary://demo", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
