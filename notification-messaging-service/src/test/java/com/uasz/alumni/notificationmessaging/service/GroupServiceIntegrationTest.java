package com.uasz.alumni.notificationmessaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.notificationmessaging.config.AttachmentStorageConfig;
import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.repository.ChatGroupRepository;
import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import com.uasz.alumni.notificationmessaging.spi.model.Group;
import com.uasz.alumni.notificationmessaging.spi.model.GroupCreateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMember;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMemberRole;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMemberRoleUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessage;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessageType;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferences;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferencesUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSettings;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSummary;
import com.uasz.alumni.notificationmessaging.spi.model.GroupUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.MuteDuration;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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

/**
 * Règles des groupes contre un vrai PostgreSQL (embarqué, sans Docker) : migrations Flyway,
 * mapping JPA (validation du schéma), requêtes et contraintes (propriétaire unique, cascade).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GroupService.class, ChatUserDirectory.class, MessageAttachments.class, AttachmentStorageConfig.class,
        GroupServiceIntegrationTest.Horloge.class})
class GroupServiceIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = demarrer();

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void arreter() throws IOException {
        POSTGRES.close();
    }

    /** Horloge réglable : chaque test avance le temps à la main. */
    static class HorlogeReglable extends Clock {
        private Instant instant = Instant.parse("2026-11-18T09:00:00Z");

        void avancer(Duration d) { instant = instant.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @TestConfiguration
    static class Horloge {
        @Bean
        HorlogeReglable clock() {
            return new HorlogeReglable();
        }
    }

    @Autowired GroupService service;
    @Autowired ChatUserDirectory directory;
    @Autowired ChatGroupRepository groupRepository;
    @Autowired HorlogeReglable horloge;

    private AuthenticatedUser fatou;   // enseignante, crée les groupes
    private AuthenticatedUser awa;     // Alumni
    private AuthenticatedUser moussa;  // étudiant
    private AuthenticatedUser intrus;  // jamais membre

    @BeforeEach
    void utilisateurs() {
        fatou = connecte("Fatou Sow", "ENSEIGNANT");
        awa = connecte("Awa Diop", "ALUMNI");
        moussa = connecte("Moussa Ndiaye", "ETUDIANT");
        intrus = connecte("Ibou Ba", "ETUDIANT");
    }

    private AuthenticatedUser connecte(String nom, String role) {
        AuthenticatedUser u = new AuthenticatedUser(UUID.randomUUID(), nom.toLowerCase() + "@uasz.sn", role, nom);
        directory.remember(u);
        return u;
    }

    private Group creer(String nom, AuthenticatedUser... invites) {
        return service.createGroup(new GroupCreateRequest(nom)
                .memberIds(java.util.Arrays.stream(invites).map(AuthenticatedUser::id).toList()), fatou);
    }

    /** Laisse passer une microseconde : deux événements successifs n'ont pas le même horodatage. */
    private void plusTard() {
        horloge.avancer(Duration.ofSeconds(1));
    }

    private static void refuse(Runnable action, HttpStatus statut) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(statut));
    }

    private List<String> fil(Group g, AuthenticatedUser lecteur) {
        return service.getMessages(g.getId(), 0, 50, lecteur).getContent().stream()
                .map(m -> m.getType() + ":" + m.getContent()).toList();
    }

    private GroupMember membre(Group g, AuthenticatedUser u) {
        return g.getMembers().stream().filter(m -> m.getUserId().equals(u.id())).findFirst().orElseThrow();
    }

    @Nested
    class Creation {

        @Test
        void leCreateurDevientProprietaireEtLesInvitesMembres() {
            Group g = creer("  Promo GL 2025  ", awa, moussa, awa);

            assertThat(g.getName()).isEqualTo("Promo GL 2025");
            assertThat(g.getOwnerId()).isEqualTo(fatou.id());
            assertThat(g.getMembers()).hasSize(3);
            assertThat(membre(g, fatou).getGroupRole()).isEqualTo(GroupMemberRole.OWNER);
            assertThat(membre(g, awa).getGroupRole()).isEqualTo(GroupMemberRole.MEMBER);
            assertThat(membre(g, awa).getUserName()).isEqualTo("Awa Diop");
            assertThat(membre(g, moussa).getUserRole()).isEqualTo("ETUDIANT");
            assertThat(g.getSettings()).isEqualTo(new GroupSettings()
                    .onlyAdminsCanSend(false).onlyAdminsCanEditInfo(true).onlyAdminsCanAddMembers(true));
            assertThat(fil(g, awa)).containsExactly("SYSTEM:Fatou Sow a créé le groupe « Promo GL 2025 »");
        }

        @Test
        void unNomTropCourtOuFaitDEspacesEstRefuse() {
            refuse(() -> creer("ab"), HttpStatus.BAD_REQUEST);
            refuse(() -> creer("   "), HttpStatus.BAD_REQUEST);
        }

        @Test
        void unInviteJamaisConnecteApparaitSansNom() {
            UUID inconnu = UUID.randomUUID();
            Group g = service.createGroup(new GroupCreateRequest("Club Data").memberIds(List.of(inconnu)), fatou);
            assertThat(g.getMembers()).filteredOn(m -> m.getUserId().equals(inconnu))
                    .singleElement().satisfies(m -> assertThat(m.getUserName()).isNull());
        }
    }

    @Nested
    class AccesReserveAuxMembres {

        @Test
        void unNonMembreNeVoitNiLeGroupeNiSesMessages() {
            Group g = creer("Promo GL", awa);
            refuse(() -> service.getGroup(g.getId(), intrus), HttpStatus.FORBIDDEN);
            refuse(() -> service.getMessages(g.getId(), 0, 30, intrus), HttpStatus.FORBIDDEN);
            refuse(() -> service.sendMessage(g.getId(), "Bonjour", intrus), HttpStatus.FORBIDDEN);
            refuse(() -> service.markAsRead(g.getId(), intrus), HttpStatus.FORBIDDEN);
            refuse(() -> service.removeMember(g.getId(), intrus.id(), intrus), HttpStatus.FORBIDDEN);
            assertThat(service.listGroups(intrus)).isEmpty();
        }

        @Test
        void unGroupeInconnuDonne404() {
            refuse(() -> service.getGroup(UUID.randomUUID(), fatou), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class Messages {

        @Test
        void laPage0ContientLesPlusRecentsDansLOrdreChronologique() {
            Group g = creer("Promo GL", awa);
            for (int i = 1; i <= 5; i++) {
                plusTard();
                service.sendMessage(g.getId(), "m" + i, i % 2 == 0 ? awa : fatou);
            }
            var page0 = service.getMessages(g.getId(), 0, 2, awa);
            var page1 = service.getMessages(g.getId(), 1, 2, awa);

            assertThat(page0.getContent()).extracting(GroupMessage::getContent).containsExactly("m4", "m5");
            assertThat(page1.getContent()).extracting(GroupMessage::getContent).containsExactly("m2", "m3");
            assertThat(page0.getTotalElements()).isEqualTo(6); // 5 messages + la création
            assertThat(page0.getContent().getFirst().getSenderName()).isEqualTo("Awa Diop");
            refuse(() -> service.getMessages(g.getId(), 0, 500, awa), HttpStatus.BAD_REQUEST);
        }

        @Test
        void unMessageVideOuTropLongEstRefuse() {
            Group g = creer("Promo GL", awa);
            refuse(() -> service.sendMessage(g.getId(), "   ", awa), HttpStatus.BAD_REQUEST);
            refuse(() -> service.sendMessage(g.getId(), "x".repeat(4001), awa), HttpStatus.BAD_REQUEST);
            assertThat(service.sendMessage(g.getId(), "  Salut  ", awa).getContent()).isEqualTo("Salut");
        }

        @Test
        void lesNonLusComptentLesMessagesDesAutresDepuisLaDerniereLecture() {
            Group g = creer("Promo GL", awa, moussa);
            plusTard();
            service.sendMessage(g.getId(), "Bienvenue", fatou);
            plusTard();
            service.sendMessage(g.getId(), "Merci", awa);

            assertThat(nonLus(moussa, g)).isEqualTo(2);
            assertThat(nonLus(awa, g)).isZero();       // répondre vaut lecture du fil
            assertThat(nonLus(fatou, g)).isEqualTo(1); // la réponse d'Awa, pas son propre message

            service.markAsRead(g.getId(), moussa);
            assertThat(nonLus(moussa, g)).isZero();
        }

        @Test
        void suppressionPourTousParLAuteurOuUnAdministrateur() {
            Group g = creer("Promo GL", awa, moussa);
            plusTard();
            GroupMessage deAwa = service.sendMessage(g.getId(), "Oups", awa);
            GroupMessage deMoussa = service.sendMessage(g.getId(), "Coucou", moussa);

            refuse(() -> service.deleteMessage(g.getId(), deAwa.getId(), moussa), HttpStatus.FORBIDDEN);
            service.deleteMessage(g.getId(), deAwa.getId(), awa);
            service.deleteMessage(g.getId(), deMoussa.getId(), fatou); // propriétaire

            List<GroupMessage> texte = service.getMessages(g.getId(), 0, 30, awa).getContent().stream()
                    .filter(m -> m.getType() == GroupMessageType.TEXT).toList();
            assertThat(texte).allSatisfy(m -> {
                assertThat(m.getDeleted()).isTrue();
                assertThat(m.getContent()).isNull();
            });
            UUID systeme = service.getMessages(g.getId(), 0, 30, awa).getContent().getFirst().getId();
            refuse(() -> service.deleteMessage(g.getId(), systeme, fatou), HttpStatus.BAD_REQUEST);
            refuse(() -> service.deleteMessage(g.getId(), UUID.randomUUID(), fatou), HttpStatus.NOT_FOUND);
        }

        @Test
        void seulLAuteurModifieSonMessageDansLes15Minutes() {
            Group g = creer("Promo GL", awa, moussa);
            plusTard();
            GroupMessage m = service.sendMessage(g.getId(), "Réunion à 10h", awa);

            horloge.avancer(Duration.ofMinutes(10));
            GroupMessage modifie = service.editMessage(g.getId(), m.getId(), "Réunion à 11h", awa);
            assertThat(modifie.getContent()).isEqualTo("Réunion à 11h");
            assertThat(modifie.getEditedAt()).isEqualTo(horloge.instant().atOffset(ZoneOffset.UTC));
            assertThat(fil(g, moussa)).contains("TEXT:Réunion à 11h");

            // Même le propriétaire ne réécrit pas le message d'un autre.
            refuse(() -> service.editMessage(g.getId(), m.getId(), "Autre", fatou), HttpStatus.FORBIDDEN);
            refuse(() -> service.editMessage(g.getId(), m.getId(), "  ", awa), HttpStatus.BAD_REQUEST);
            UUID systeme = service.getMessages(g.getId(), 0, 30, awa).getContent().getFirst().getId();
            refuse(() -> service.editMessage(g.getId(), systeme, "Autre", fatou), HttpStatus.BAD_REQUEST);

            horloge.avancer(Duration.ofMinutes(6));
            refuse(() -> service.editMessage(g.getId(), m.getId(), "Trop tard", awa), HttpStatus.BAD_REQUEST);

            // Écriture réservée aux administrateurs : le membre ne modifie plus ses messages.
            GroupMessage deMoussa = service.sendMessage(g.getId(), "Présent", moussa);
            service.updateSettings(g.getId(), new GroupSettings().onlyAdminsCanSend(true), fatou);
            refuse(() -> service.editMessage(g.getId(), deMoussa.getId(), "Absent", moussa), HttpStatus.FORBIDDEN);
        }

        private int nonLus(AuthenticatedUser u, Group g) {
            return service.listGroups(u).stream().filter(s -> s.getId().equals(g.getId()))
                    .findFirst().orElseThrow().getUnreadCount();
        }
    }

    @Nested
    class ReglagesEtRoles {

        @Test
        void seulsLesAdministrateursEcriventQuandLeReglageEstActif() {
            Group g = creer("Annonces", awa, moussa);
            refuse(() -> service.updateSettings(g.getId(), new GroupSettings().onlyAdminsCanSend(true), awa),
                    HttpStatus.FORBIDDEN);
            service.updateSettings(g.getId(), new GroupSettings().onlyAdminsCanSend(true), fatou);
            service.updateMemberRole(g.getId(), awa.id(), new GroupMemberRoleUpdateRequest(
                    GroupMemberRoleUpdateRequest.GroupRoleEnum.ADMIN), fatou);

            refuse(() -> service.sendMessage(g.getId(), "Puis-je ?", moussa), HttpStatus.FORBIDDEN);
            assertThat(service.sendMessage(g.getId(), "Annonce", awa).getContent()).isEqualTo("Annonce");
            assertThat(fil(g, moussa)).contains(
                    "SYSTEM:Fatou Sow a modifié les réglages : seuls les administrateurs peuvent envoyer des messages",
                    "SYSTEM:Fatou Sow a nommé Awa Diop administrateur");
        }

        @Test
        void lesInfosEtLesAjoutsSOuvrentAuxMembresSelonLesReglages() {
            Group g = creer("Promo GL", awa);
            refuse(() -> service.updateGroup(g.getId(), new GroupUpdateRequest().name("Promo GL 2026"), awa),
                    HttpStatus.FORBIDDEN);
            refuse(() -> service.addMembers(g.getId(), List.of(moussa.id()), awa), HttpStatus.FORBIDDEN);

            service.updateSettings(g.getId(), new GroupSettings().onlyAdminsCanEditInfo(false)
                    .onlyAdminsCanAddMembers(false), fatou);
            assertThat(service.updateGroup(g.getId(), new GroupUpdateRequest().name("Promo GL 2026"), awa).getName())
                    .isEqualTo("Promo GL 2026");
            Group apres = service.addMembers(g.getId(), List.of(moussa.id(), awa.id()), awa);
            assertThat(apres.getMembers()).hasSize(3); // awa, déjà membre, est ignorée
            assertThat(fil(g, moussa)).contains("SYSTEM:Awa Diop a ajouté Moussa Ndiaye",
                    "SYSTEM:Awa Diop a renommé le groupe en « Promo GL 2026 »");
        }

        @Test
        void seulLeProprietaireNommeDesAdministrateursEtSonRoleNeChangePas() {
            Group g = creer("Promo GL", awa, moussa);
            service.updateMemberRole(g.getId(), awa.id(), new GroupMemberRoleUpdateRequest(
                    GroupMemberRoleUpdateRequest.GroupRoleEnum.ADMIN), fatou);
            refuse(() -> service.updateMemberRole(g.getId(), moussa.id(), new GroupMemberRoleUpdateRequest(
                    GroupMemberRoleUpdateRequest.GroupRoleEnum.ADMIN), awa), HttpStatus.FORBIDDEN);
            refuse(() -> service.updateMemberRole(g.getId(), fatou.id(), new GroupMemberRoleUpdateRequest(
                    GroupMemberRoleUpdateRequest.GroupRoleEnum.MEMBER), fatou), HttpStatus.BAD_REQUEST);
            refuse(() -> service.updateMemberRole(g.getId(), intrus.id(), new GroupMemberRoleUpdateRequest(
                    GroupMemberRoleUpdateRequest.GroupRoleEnum.ADMIN), fatou), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DepartsEtSuppression {

        @Test
        void unAdminRetireUnMembreMaisJamaisLeProprietaire() {
            Group g = creer("Promo GL", awa, moussa);
            service.updateMemberRole(g.getId(), awa.id(), new GroupMemberRoleUpdateRequest(
                    GroupMemberRoleUpdateRequest.GroupRoleEnum.ADMIN), fatou);

            refuse(() -> service.removeMember(g.getId(), fatou.id(), awa), HttpStatus.FORBIDDEN);
            refuse(() -> service.removeMember(g.getId(), awa.id(), moussa), HttpStatus.FORBIDDEN);
            service.removeMember(g.getId(), moussa.id(), awa);

            refuse(() -> service.getGroup(g.getId(), moussa), HttpStatus.FORBIDDEN);
            assertThat(fil(g, awa)).contains("SYSTEM:Awa Diop a retiré Moussa Ndiaye");
        }

        @Test
        void quandLeProprietaireQuitteUnAdminPuisLePlusAncienMembreReprend() {
            Group g = creer("Promo GL", moussa, awa);
            service.updateMemberRole(g.getId(), awa.id(), new GroupMemberRoleUpdateRequest(
                    GroupMemberRoleUpdateRequest.GroupRoleEnum.ADMIN), fatou);

            service.removeMember(g.getId(), fatou.id(), fatou);
            Group apres = service.getGroup(g.getId(), awa);
            assertThat(apres.getOwnerId()).isEqualTo(awa.id());
            assertThat(membre(apres, awa).getGroupRole()).isEqualTo(GroupMemberRole.OWNER);
            assertThat(fil(g, awa)).contains("SYSTEM:Fatou Sow a quitté le groupe",
                    "SYSTEM:Awa Diop est maintenant propriétaire du groupe");

            service.removeMember(g.getId(), awa.id(), awa);
            assertThat(service.getGroup(g.getId(), moussa).getOwnerId()).isEqualTo(moussa.id());
        }

        @Test
        void leGroupeDisparaitAvecSonDernierMembre() {
            Group g = creer("Solo");
            service.removeMember(g.getId(), fatou.id(), fatou);
            assertThat(groupRepository.existsById(g.getId())).isFalse();
        }

        @Test
        void seulsLeProprietaireOuUnAdministrateurDeLaPlateformeSuppriment() {
            Group g = creer("Promo GL", awa);
            refuse(() -> service.deleteGroup(g.getId(), awa), HttpStatus.FORBIDDEN);
            refuse(() -> service.deleteGroup(g.getId(), intrus), HttpStatus.FORBIDDEN);

            AuthenticatedUser admin = connecte("Admin UASZ", "ADMINISTRATEUR");
            service.deleteGroup(g.getId(), admin);
            assertThat(groupRepository.existsById(g.getId())).isFalse();
            refuse(() -> service.getGroup(g.getId(), fatou), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class Preferences {

        @Test
        void laSourdineExpireEtToujoursResteActive() {
            Group g = creer("Promo GL", awa);
            GroupPreferences p = service.updatePreferences(g.getId(),
                    new GroupPreferencesUpdateRequest().mute(MuteDuration.EIGHT_HOURS), awa);
            assertThat(p.getMuted()).isTrue();
            assertThat(p.getMutedUntil()).isEqualTo(horloge.instant().plus(Duration.ofHours(8)).atOffset(ZoneOffset.UTC));

            horloge.avancer(Duration.ofHours(9));
            assertThat(service.getGroup(g.getId(), awa).getPreferences().getMuted()).isFalse();

            p = service.updatePreferences(g.getId(), new GroupPreferencesUpdateRequest().mute(MuteDuration.ALWAYS), awa);
            assertThat(p.getMuted()).isTrue();
            assertThat(p.getMutedUntil()).isNull();
            assertThat(service.getGroup(g.getId(), fatou).getPreferences().getMuted()).isFalse(); // personnelle
        }

        @Test
        void lesGroupesEpinglesPassentDevantPuisLesPlusRecents() {
            Group epingle = creer("Ancien épinglé", awa);
            plusTard();
            Group actif = creer("Groupe actif", awa);
            plusTard();
            creer("Groupe calme", awa);
            plusTard();
            service.sendMessage(actif.getId(), "Du nouveau", fatou);
            service.updatePreferences(epingle.getId(), new GroupPreferencesUpdateRequest().pinned(true).archived(true), awa);

            List<GroupSummary> liste = service.listGroups(awa);
            assertThat(liste).extracting(GroupSummary::getName)
                    .containsExactly("Ancien épinglé", "Groupe actif", "Groupe calme");
            assertThat(liste.getFirst().getPreferences().getPinned()).isTrue();
            assertThat(liste.getFirst().getPreferences().getArchived()).isTrue();
            assertThat(liste.get(1).getLastMessage()).isEqualTo("Du nouveau");
            assertThat(liste.get(1).getLastMessageSenderName()).isEqualTo("Fatou Sow");
            assertThat(liste.get(1).getLastMessageType()).isEqualTo(GroupMessageType.TEXT);
            assertThat(liste.get(2).getLastMessageType()).isEqualTo(GroupMessageType.SYSTEM);
        }
    }
}
