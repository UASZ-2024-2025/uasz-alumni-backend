package com.uasz.alumni.notificationmessaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import com.uasz.alumni.notificationmessaging.repository.NotificationRepository;
import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import com.uasz.alumni.notificationmessaging.spi.model.SendNotificationRequest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** ALUM-135 contre un vrai PostgreSQL (migration V4, mapping JPA) : enregistrement, canal EMAIL, validation. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationService.class, ChatUserDirectory.class, NotificationServiceIntegrationTest.Horloge.class})
@RecordApplicationEvents
class NotificationServiceIntegrationTest {

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

    @TestConfiguration
    static class Horloge {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-21T09:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired NotificationService service;
    @Autowired NotificationRepository notifications;
    @Autowired ChatUserDirectory directory;
    @Autowired ApplicationEvents events;

    private final UUID awa = UUID.randomUUID();

    private SendNotificationRequest request(NotificationChannel channel) {
        return new SendNotificationRequest(awa, NotificationType.APPLICATION_STATUS_CHANGED, " Mise à jour ",
                "Votre candidature est présélectionnée.").channel(channel).referenceId("app-1");
    }

    @Test
    void uneNotificationEstEnregistreeNonLue_etApparaitDansLeCentre() {
        NotificationEntity saved = service.send(request(null));

        NotificationEntity lue = notifications.findById(saved.getId()).orElseThrow();
        assertThat(lue.getUserId()).isEqualTo(awa);
        assertThat(lue.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(lue.getTitle()).isEqualTo("Mise à jour");
        assertThat(lue.getReferenceId()).isEqualTo("app-1");
        assertThat(lue.isRead()).isFalse();
        assertThat(events.stream(EmailNotificationRequested.class)).isEmpty();
    }

    @Test
    void leCanalEmailDemandeAussiUnEmail() {
        service.send(request(NotificationChannel.EMAIL));

        assertThat(events.stream(EmailNotificationRequested.class))
                .singleElement()
                .satisfies(e -> assertThat(e.userId()).isEqualTo(awa));
    }

    @Test
    void uneDemandeIncompleteOuTropLongueEstRefusee() {
        SendNotificationRequest sansTitre = request(null).title("  ");
        SendNotificationRequest tropLong = request(null).content("x".repeat(4001));
        for (SendNotificationRequest r : List.of(sansTitre, tropLong)) {
            assertThatThrownBy(() -> service.send(r)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void lAnnuaireRetientLEmailDuJeton_sansLEffacerQuandIlManque() {
        directory.remember(new AuthenticatedUser(awa, "awa@zig.univ.sn", "ETUDIANT", "Awa Ndiaye"));
        directory.remember(new AuthenticatedUser(awa, null, "ETUDIANT", "Awa Ndiaye"));

        assertThat(directory.find(List.of(awa)).get(awa).getEmail()).isEqualTo("awa@zig.univ.sn");
    }
}
