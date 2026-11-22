package com.uasz.alumni.notificationmessaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferences;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferencesUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.MuteDuration;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessage;
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

/** Messagerie privée alignée sur les groupes : suppression pour les deux, modification, préférences. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PrivateMessagingService.class, ChatUserDirectory.class, PrivateMessagingServiceIntegrationTest.Horloge.class})
class PrivateMessagingServiceIntegrationTest {

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
    }

    @AfterAll
    static void arreter() throws IOException {
        POSTGRES.close();
    }

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

    @Autowired PrivateMessagingService service;
    @Autowired HorlogeReglable horloge;

    private final UUID awa = UUID.randomUUID();
    private final UUID moussa = UUID.randomUUID();

    private static void refuse(Runnable action, HttpStatus statut, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(statut);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    private PrivateMessage dernierMessage(UUID lecteur, UUID autre) {
        return service.getConversation(lecteur, autre, 0, 10).getContent().getFirst();
    }

    @Test
    void lAuteurModifieSonMessageDansLes15Minutes() {
        PrivateMessage m = service.send(awa, moussa, "Rendez-vous à 10h");
        assertThat(m.getEditedAt()).isNull();

        horloge.avancer(Duration.ofMinutes(5));
        PrivateMessage modifie = service.edit(awa, m.getId(), "  Rendez-vous à 11h ");

        assertThat(modifie.getContent()).isEqualTo("Rendez-vous à 11h");
        assertThat(modifie.getEditedAt()).isEqualTo(horloge.instant().atOffset(ZoneOffset.UTC));
        assertThat(modifie.getCreatedAt()).isEqualTo(m.getCreatedAt());
        PrivateMessage vuParMoussa = dernierMessage(moussa, awa);
        assertThat(vuParMoussa.getContent()).isEqualTo("Rendez-vous à 11h");
        assertThat(vuParMoussa.getEditedAt()).isNotNull();
    }

    @Test
    void unTexteIdentiqueNeMarquePasLeMessageCommeModifie() {
        PrivateMessage m = service.send(awa, moussa, "Bonjour");
        assertThat(service.edit(awa, m.getId(), "Bonjour ").getEditedAt()).isNull();
    }

    @Test
    void modificationRefuseeHorsDelaiAutreAuteurSupprimeOuVide() {
        PrivateMessage m = service.send(awa, moussa, "Bonjour");

        refuse(() -> service.edit(moussa, m.getId(), "Piraté"), HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND");
        refuse(() -> service.edit(awa, m.getId(), "   "), HttpStatus.BAD_REQUEST, "EMPTY_MESSAGE");
        refuse(() -> service.edit(awa, m.getId(), "x".repeat(5001)), HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG");

        horloge.avancer(Duration.ofMinutes(16));
        refuse(() -> service.edit(awa, m.getId(), "Trop tard"), HttpStatus.BAD_REQUEST, "EDIT_WINDOW_EXPIRED");

        PrivateMessage recent = service.send(awa, moussa, "Oups");
        service.delete(awa, recent.getId());
        refuse(() -> service.edit(awa, recent.getId(), "Rattrapage"), HttpStatus.BAD_REQUEST, "MESSAGE_DELETED");
    }

    @Test
    void unMessageSupprimeResteDansLeFilSansContenu() {
        service.send(awa, moussa, "Bonjour");
        horloge.avancer(Duration.ofSeconds(1));
        PrivateMessage oups = service.send(awa, moussa, "Message envoyé par erreur");

        refuse(() -> service.delete(moussa, oups.getId()), HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND");
        service.delete(awa, oups.getId());

        PrivateMessage vu = dernierMessage(moussa, awa);
        assertThat(vu.getId()).isEqualTo(oups.getId());
        assertThat(vu.getDeleted()).isTrue();
        assertThat(vu.getContent()).isNull();
        ConversationSummary resume = service.listConversations(moussa).getFirst();
        assertThat(resume.getLastMessageDeleted()).isTrue();
        assertThat(resume.getLastMessage()).isNull();
        assertThat(resume.getLastMessageSenderId()).isEqualTo(awa);
    }

    @Test
    void preferencesPersonnellesEtConversationsEpingleesEnTete() {
        UUID fatou = UUID.randomUUID();
        service.send(fatou, awa, "Ancienne conversation");
        horloge.avancer(Duration.ofMinutes(1));
        service.send(moussa, awa, "Conversation récente");

        assertThat(service.listConversations(awa)).extracting(ConversationSummary::getRecipientId)
                .containsExactly(moussa, fatou);
        assertThat(service.listConversations(awa).getFirst().getPreferences().getPinned()).isFalse();

        GroupPreferences p = service.updatePreferences(awa, fatou,
                new GroupPreferencesUpdateRequest().pinned(true).archived(true).mute(MuteDuration.EIGHT_HOURS));
        assertThat(p.getPinned()).isTrue();
        assertThat(p.getArchived()).isTrue();
        assertThat(p.getMuted()).isTrue();
        assertThat(p.getMutedUntil()).isEqualTo(horloge.instant().plus(Duration.ofHours(8)).atOffset(ZoneOffset.UTC));

        List<ConversationSummary> liste = service.listConversations(awa);
        assertThat(liste).extracting(ConversationSummary::getRecipientId).containsExactly(fatou, moussa);
        assertThat(liste.getFirst().getPreferences().getArchived()).isTrue();
        // Personnelles : Fatou ne voit rien de ces réglages.
        assertThat(service.listConversations(fatou).getFirst().getPreferences().getPinned()).isFalse();

        horloge.avancer(Duration.ofHours(9));
        assertThat(service.listConversations(awa).getFirst().getPreferences().getMuted()).isFalse();
        // Seuls les champs fournis changent.
        p = service.updatePreferences(awa, fatou, new GroupPreferencesUpdateRequest().archived(false));
        assertThat(p.getPinned()).isTrue();
        assertThat(p.getArchived()).isFalse();

        refuse(() -> service.updatePreferences(awa, awa, new GroupPreferencesUpdateRequest().pinned(true)),
                HttpStatus.BAD_REQUEST, "CANNOT_MESSAGE_SELF");
    }
}
