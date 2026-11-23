package com.uasz.alumni.notificationmessaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.StoredAttachment;
import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentKind;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUpload;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadRequest;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadTicket;
import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
import com.uasz.alumni.notificationmessaging.spi.model.Group;
import com.uasz.alumni.notificationmessaging.spi.model.GroupCreateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessage;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessageType;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSettings;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSummary;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessage;
import com.uasz.alumni.notificationmessaging.spi.model.SendMessageRequest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

/** ALUM-261 : stickers et pièces jointes dans les groupes et la messagerie privée (stockage simulé). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GroupService.class, PrivateMessagingService.class, ChatUserDirectory.class, MessageAttachments.class,
        MessageAttachmentsIntegrationTest.Config.class})
class MessageAttachmentsIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = demarrer();
    private static final String DOSSIER = "alumni-uasz/messages";

    private static EmbeddedPostgres demarrer() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("PostgreSQL embarqué indisponible", e);
        }
    }

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("attachments.max-image-bytes", () -> 1000);
        registry.add("attachments.max-file-bytes", () -> 2000);
        registry.add("attachments.max-audio-seconds", () -> 60);
    }

    @AfterAll
    static void arreter() throws IOException {
        POSTGRES.close();
    }

    /** Stockage simulé : une réponse est authentique si sa signature vaut « sig-<publicId>-<version> ». */
    static class FauxStockage implements AttachmentStorage {
        boolean actif = true;
        boolean injoignable = false;
        final List<String> effaces = new CopyOnWriteArrayList<>();
        /** Fichiers réellement déposés : ce que le stockage en dit, quoi qu'annonce le navigateur. */
        final Map<String, StoredFile> fichiers = new ConcurrentHashMap<>();

        @Override
        public StoredFile describe(String publicId, AttachmentKind kind) {
            if (injoignable) {
                throw new StorageUnavailableException("panne", null);
            }
            return fichiers.get(publicId);
        }

        @Override public boolean enabled() { return actif; }

        @Override
        public AttachmentUploadTicket ticket(String publicId, AttachmentKind kind, String allowedFormats) {
            return new AttachmentUploadTicket().publicId(publicId).signature("signe").allowedFormats(allowedFormats)
                    .uploadUrl("https://stockage/" + CloudinaryAttachmentStorage.resourceType(kind) + "/upload");
        }

        @Override
        public boolean isAuthentic(String publicId, long version, String signature) {
            return ("sig-" + publicId + "-" + version).equals(signature);
        }

        @Override public String url(StoredAttachment a) { return "https://stockage/" + a.getPublicId(); }

        @Override
        public String previewUrl(StoredAttachment a) {
            return a.getKind() == AttachmentKind.IMAGE ? "https://stockage/apercu/" + a.getPublicId() : null;
        }

        @Override public void delete(StoredAttachment a) { effaces.add(a.getPublicId()); }
    }

    @TestConfiguration
    static class Config {
        @Bean FauxStockage stockage() { return new FauxStockage(); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC); }
    }

    @Autowired GroupService groups;
    @Autowired PrivateMessagingService prive;
    @Autowired MessageAttachments attachments;
    @Autowired ChatUserDirectory directory;
    @Autowired FauxStockage stockage;

    private AuthenticatedUser fatou;
    private AuthenticatedUser awa;
    private AuthenticatedUser intrus;

    @BeforeEach
    void utilisateurs() {
        stockage.actif = true;
        stockage.injoignable = false;
        stockage.effaces.clear();
        stockage.fichiers.clear();
        fatou = connecte("Fatou Sow", "ENSEIGNANT");
        awa = connecte("Awa Diop", "ALUMNI");
        intrus = connecte("Ibou Ba", "ETUDIANT");
    }

    private AuthenticatedUser connecte(String nom, String role) {
        AuthenticatedUser u = new AuthenticatedUser(UUID.randomUUID(), nom.toLowerCase().replace(' ', '.') + "@uasz.sn", role, nom);
        directory.remember(u);
        return u;
    }

    private Group groupe() {
        return groups.createGroup(new GroupCreateRequest("Promo 2026").memberIds(List.of(awa.id())), fatou);
    }

    private static void refuse(Runnable action, HttpStatus statut, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(statut);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    /**
     * Fichier déposé chez le stockage pour {@code auteur} ({@code taille} octets ; image jpg 1200x800, vocal de 10 s),
     * et réponse d'envoi telle que le navigateur la recopie.
     */
    private AttachmentUpload envoi(AuthenticatedUser auteur, AttachmentKind kind, String suffixe, long taille) {
        String publicId = DOSSIER + "/" + auteur.id() + "/" + UUID.randomUUID() + suffixe;
        stockage.fichiers.put(publicId, switch (kind) {
            case IMAGE -> new AttachmentStorage.StoredFile(taille, "jpg", null, 1200, 800);
            case AUDIO -> new AttachmentStorage.StoredFile(taille, "webm", 10.0, null, null);
            case FILE -> new AttachmentStorage.StoredFile(taille, null, null, null, null);
        });
        return new AttachmentUpload().kind(kind).publicId(publicId).version(7L).signature("sig-" + publicId + "-7")
                .fileName("fichier" + suffixe).sizeBytes(taille);
    }

    /** Ce que le stockage dit vraiment du fichier (remplace la description par défaut d'{@link #envoi}). */
    private AttachmentUpload reel(AttachmentUpload upload, AttachmentStorage.StoredFile fichier) {
        stockage.fichiers.put(upload.getPublicId(), fichier);
        return upload;
    }

    private static SendMessageRequest piece(AttachmentUpload upload) {
        return new SendMessageRequest().attachment(upload);
    }

    @Nested
    class AutorisationDEnvoi {

        @Test
        void lesReglagesIndiquentSiLesPiecesJointesSontActivees() {
            assertThat(attachments.settings().getEnabled()).isTrue();
            assertThat(attachments.settings().getMaxAudioSeconds()).isEqualTo(60);
            assertThat(attachments.settings().getFileExtensions()).contains("pdf", "docx");
            stockage.actif = false;
            assertThat(attachments.settings().getEnabled()).isFalse();
        }

        @Test
        void unMembreRecoitUnEmplacementASonNomDocumentAvecSonExtension() {
            Group g = groupe();
            AttachmentUploadTicket t = attachments.ticket(new AttachmentUploadRequest(AttachmentKind.FILE, "Rapport Stage.PDF")
                    .groupId(g.getId()), awa.id());
            assertThat(t.getPublicId()).startsWith(DOSSIER + "/" + awa.id() + "/").endsWith(".pdf");
            assertThat(t.getAllowedFormats()).isNull();
            assertThat(t.getUploadUrl()).endsWith("/raw/upload");

            AttachmentUploadTicket image = attachments.ticket(new AttachmentUploadRequest(AttachmentKind.IMAGE, "photo.jpg")
                    .recipientId(fatou.id()), awa.id());
            assertThat(image.getPublicId()).doesNotContain(".jpg");
            assertThat(image.getAllowedFormats()).contains("png", "heic");
        }

        @Test
        void refusDesNonMembresDesGroupesFermesEtDesFichiersNonAcceptes() {
            Group g = groupe();
            refuse(() -> attachments.ticket(new AttachmentUploadRequest(AttachmentKind.IMAGE, "a.png").groupId(g.getId()),
                    intrus.id()), HttpStatus.FORBIDDEN, "FORBIDDEN");
            groups.updateSettings(g.getId(), new GroupSettings().onlyAdminsCanSend(true), fatou);
            refuse(() -> attachments.ticket(new AttachmentUploadRequest(AttachmentKind.IMAGE, "a.png").groupId(g.getId()),
                    awa.id()), HttpStatus.FORBIDDEN, "FORBIDDEN");
            refuse(() -> attachments.ticket(new AttachmentUploadRequest(AttachmentKind.FILE, "virus.exe")
                    .recipientId(fatou.id()), awa.id()), HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE");
            refuse(() -> attachments.ticket(new AttachmentUploadRequest(AttachmentKind.FILE, "a.pdf")
                    .recipientId(awa.id()), awa.id()), HttpStatus.BAD_REQUEST, "CANNOT_MESSAGE_SELF");
            refuse(() -> attachments.ticket(new AttachmentUploadRequest(AttachmentKind.FILE, "a.pdf")
                    .recipientId(fatou.id()).groupId(g.getId()), awa.id()), HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT");
            refuse(() -> attachments.ticket(new AttachmentUploadRequest(AttachmentKind.IMAGE, "a.png")
                    .groupId(UUID.randomUUID()), awa.id()), HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND");
        }

        @Test
        void sansStockageConfigureLesPiecesJointesSontIndisponibles() {
            stockage.actif = false;
            refuse(() -> attachments.ticket(new AttachmentUploadRequest(AttachmentKind.IMAGE, "a.png")
                    .recipientId(fatou.id()), awa.id()), HttpStatus.SERVICE_UNAVAILABLE, "ATTACHMENTS_DISABLED");
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(envoi(awa, AttachmentKind.FILE, ".pdf", 10))),
                    HttpStatus.SERVICE_UNAVAILABLE, "ATTACHMENTS_DISABLED");
        }
    }

    @Nested
    class MessagePrive {

        @Test
        void unStickerSEnvoieSeulEtNeSeModifiePas() {
            PrivateMessage m = prive.send(awa.id(), fatou.id(), new SendMessageRequest().sticker("bravo"));
            assertThat(m.getType()).isEqualTo(GroupMessageType.STICKER);
            assertThat(m.getSticker()).isEqualTo("bravo");
            assertThat(m.getContent()).isNull();

            refuse(() -> prive.edit(awa.id(), m.getId(), "Bravo !"), HttpStatus.BAD_REQUEST, "INVALID_MESSAGE");
            refuse(() -> prive.send(awa.id(), fatou.id(), new SendMessageRequest().sticker("bravo").content("Salut")),
                    HttpStatus.BAD_REQUEST, "INVALID_MESSAGE");
            refuse(() -> prive.send(awa.id(), fatou.id(), new SendMessageRequest().sticker("Bravo!")),
                    HttpStatus.BAD_REQUEST, "INVALID_STICKER");
            refuse(() -> prive.send(awa.id(), fatou.id(), new SendMessageRequest().sticker("bravo")
                    .attachment(envoi(awa, AttachmentKind.FILE, ".pdf", 10))), HttpStatus.BAD_REQUEST, "INVALID_MESSAGE");
        }

        @Test
        void uneImageAvecLegendeEstVueParLesDeuxAvecSesLiens() {
            AttachmentUpload photo = envoi(awa, AttachmentKind.IMAGE, "", 800).format("JPG").width(1200).height(800);
            PrivateMessage m = prive.send(awa.id(), fatou.id(), piece(photo).content("  Ma soutenance  "));

            assertThat(m.getType()).isEqualTo(GroupMessageType.ATTACHMENT);
            assertThat(m.getContent()).isEqualTo("Ma soutenance");
            assertThat(m.getAttachment().getKind()).isEqualTo(AttachmentKind.IMAGE);
            assertThat(m.getAttachment().getFormat()).isEqualTo("jpg");
            assertThat(m.getAttachment().getWidth()).isEqualTo(1200);
            assertThat(m.getAttachment().getUrl()).isEqualTo("https://stockage/" + photo.getPublicId());
            assertThat(m.getAttachment().getPreviewUrl()).isEqualTo("https://stockage/apercu/" + photo.getPublicId());

            PrivateMessage vu = prive.getConversation(fatou.id(), awa.id(), 0, 10).getContent().getFirst();
            assertThat(vu.getAttachment().getFileName()).isEqualTo(photo.getFileName());
            ConversationSummary resume = prive.listConversations(fatou.id()).getFirst();
            assertThat(resume.getLastMessageType()).isEqualTo(GroupMessageType.ATTACHMENT);
            assertThat(resume.getLastMessageAttachmentKind()).isEqualTo(AttachmentKind.IMAGE);
        }

        @Test
        void unVocalGardeSaDureeEtUnDocumentSonExtension() {
            AttachmentUpload enregistrement = reel(envoi(awa, AttachmentKind.AUDIO, "", 500),
                    new AttachmentStorage.StoredFile(500, "webm", 12.4, 10, 10));
            PrivateMessage vocal = prive.send(awa.id(), fatou.id(), piece(enregistrement.durationSeconds(99.0)));
            assertThat(vocal.getAttachment().getDurationSeconds()).isEqualTo(12.4);
            assertThat(vocal.getAttachment().getWidth()).isNull();
            assertThat(vocal.getAttachment().getPreviewUrl()).isNull();

            AttachmentUpload doc = envoi(awa, AttachmentKind.FILE, ".docx", 1500).fileName("C:\\Users\\awa\\CV final.docx");
            PrivateMessage document = prive.send(awa.id(), fatou.id(), piece(doc));
            assertThat(document.getAttachment().getFormat()).isEqualTo("docx");
            assertThat(document.getAttachment().getFileName()).isEqualTo("CV final.docx");
        }

        @Test
        void refusDesReponsesFalsifieesEmprunteesOuTropLourdes() {
            AttachmentUpload falsifiee = envoi(awa, AttachmentKind.FILE, ".pdf", 10).signature("bidon");
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(falsifiee)), HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT");

            AttachmentUpload deFatou = envoi(fatou, AttachmentKind.FILE, ".pdf", 10);
            refuse(() -> prive.send(awa.id(), intrus.id(), piece(deFatou)), HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT");

            refuse(() -> prive.send(awa.id(), fatou.id(), piece(envoi(awa, AttachmentKind.FILE, ".pdf", 2001))),
                    HttpStatus.BAD_REQUEST, "ATTACHMENT_TOO_LARGE");
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(envoi(awa, AttachmentKind.IMAGE, "", 1001).format("png"))),
                    HttpStatus.BAD_REQUEST, "ATTACHMENT_TOO_LARGE");
            AttachmentUpload long_ = reel(envoi(awa, AttachmentKind.AUDIO, "", 10), new AttachmentStorage.StoredFile(10, "webm", 75.0, null, null));
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(long_)), HttpStatus.BAD_REQUEST, "ATTACHMENT_TOO_LARGE");
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(envoi(awa, AttachmentKind.FILE, ".exe", 10))),
                    HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE");
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(reel(envoi(awa, AttachmentKind.IMAGE, "", 10), new AttachmentStorage.StoredFile(10, "svg", null, 1, 1)).format("png"))),
                    HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE");
            refuse(() -> prive.send(awa.id(), fatou.id(), new SendMessageRequest()), HttpStatus.BAD_REQUEST, "EMPTY_MESSAGE");
        }

        @Test
        void tailleDureeEtFormatSontCeuxDuFichierStockePasCeuxAnnoncesParLeNavigateur() {
            // Le navigateur annonce 10 octets et un vocal de 3 s : le stockage a reçu 50 Mo et 10 minutes.
            AttachmentUpload gros = reel(envoi(awa, AttachmentKind.FILE, ".pdf", 10),
                    new AttachmentStorage.StoredFile(50_000_000, null, null, null, null));
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(gros)), HttpStatus.BAD_REQUEST, "ATTACHMENT_TOO_LARGE");
            AttachmentUpload long_ = reel(envoi(awa, AttachmentKind.AUDIO, "", 10).durationSeconds(3.0),
                    new AttachmentStorage.StoredFile(10, "webm", 600.0, null, null));
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(long_)), HttpStatus.BAD_REQUEST, "ATTACHMENT_TOO_LARGE");
            // un fichier refusé est effacé du stockage
            assertThat(stockage.effaces).containsExactly(gros.getPublicId(), long_.getPublicId());

            AttachmentUpload photo = envoi(awa, AttachmentKind.IMAGE, "", 100).format("gif").width(1).height(1).sizeBytes(1L);
            PrivateMessage m = prive.send(awa.id(), fatou.id(), piece(photo));
            assertThat(m.getAttachment().getFormat()).isEqualTo("jpg");
            assertThat(m.getAttachment().getSizeBytes()).isEqualTo(100L);
            assertThat(m.getAttachment().getWidth()).isEqualTo(1200);
        }

        @Test
        void unFichierAbsentDuStockageOuUnStockageEnPanneSontSignales() {
            AttachmentUpload jamaisDepose = envoi(awa, AttachmentKind.FILE, ".pdf", 10);
            stockage.fichiers.remove(jamaisDepose.getPublicId());
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(jamaisDepose)), HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT");

            AttachmentUpload vocal = reel(envoi(awa, AttachmentKind.AUDIO, "", 10), new AttachmentStorage.StoredFile(10, "webm", null, null, null));
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(vocal)), HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT");

            stockage.injoignable = true;
            refuse(() -> prive.send(awa.id(), fatou.id(), piece(envoi(awa, AttachmentKind.FILE, ".pdf", 10))),
                    HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE");
        }

        @Test
        void supprimerUnMessageEffaceSonFichierApresValidation() {
            AttachmentUpload doc = envoi(awa, AttachmentKind.FILE, ".pdf", 10);
            PrivateMessage m = prive.send(awa.id(), fatou.id(), piece(doc));
            TestTransaction.flagForCommit();
            TestTransaction.end();

            TestTransaction.start();
            prive.delete(awa.id(), m.getId());
            assertThat(stockage.effaces).isEmpty(); // pas avant la validation
            TestTransaction.flagForCommit();
            TestTransaction.end();

            await().atMost(Duration.ofSeconds(5)).until(() -> stockage.effaces.contains(doc.getPublicId()));
            TestTransaction.start();
            PrivateMessage vu = prive.getConversation(fatou.id(), awa.id(), 0, 10).getContent().getFirst();
            assertThat(vu.getDeleted()).isTrue();
            assertThat(vu.getAttachment()).isNull();
        }
    }

    @Nested
    class MessageDeGroupe {

        @Test
        void uneImageDansUnGroupeApparaitDansLeFilEtLaListe() {
            Group g = groupe();
            AttachmentUpload photo = envoi(awa, AttachmentKind.IMAGE, "", 100).format("png");
            GroupMessage m = groups.sendMessage(g.getId(), piece(photo), awa);
            assertThat(m.getType()).isEqualTo(GroupMessageType.ATTACHMENT);
            assertThat(m.getAttachment().getPreviewUrl()).isNotNull();

            GroupMessage vu = groups.getMessages(g.getId(), 0, 30, fatou).getContent().getLast();
            assertThat(vu.getAttachment().getUrl()).isEqualTo("https://stockage/" + photo.getPublicId());
            GroupSummary resume = groups.listGroups(fatou).getFirst();
            assertThat(resume.getLastMessageType()).isEqualTo(GroupMessageType.ATTACHMENT);
            assertThat(resume.getLastMessageAttachmentKind()).isEqualTo(AttachmentKind.IMAGE);
        }

        @Test
        void stickerDansUnGroupeEtModificationRefusee() {
            Group g = groupe();
            GroupMessage m = groups.sendMessage(g.getId(), new SendMessageRequest().sticker("merci"), awa);
            assertThat(m.getSticker()).isEqualTo("merci");
            refuse(() -> groups.editMessage(g.getId(), m.getId(), "Merci", awa), HttpStatus.BAD_REQUEST, "INVALID_MESSAGE");
        }

        @Test
        void laLegendeDUnePieceJointeSeModifie() {
            Group g = groupe();
            GroupMessage m = groups.sendMessage(g.getId(), piece(envoi(awa, AttachmentKind.FILE, ".pdf", 10)), awa);
            GroupMessage modifie = groups.editMessage(g.getId(), m.getId(), "Le sujet de l'examen", awa);
            assertThat(modifie.getContent()).isEqualTo("Le sujet de l'examen");
            assertThat(modifie.getAttachment()).isNotNull();
        }

        @Test
        void supprimerLeGroupeEffaceSesFichiers() {
            Group g = groupe();
            AttachmentUpload doc = envoi(awa, AttachmentKind.FILE, ".pdf", 10);
            groups.sendMessage(g.getId(), piece(doc), awa);
            TestTransaction.flagForCommit();
            TestTransaction.end();

            TestTransaction.start();
            groups.deleteGroup(g.getId(), fatou);
            TestTransaction.flagForCommit();
            TestTransaction.end();

            await().atMost(Duration.ofSeconds(5)).until(() -> stockage.effaces.contains(doc.getPublicId()));
            TestTransaction.start();
        }
    }
}
