package com.uasz.alumni.notificationmessaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.NotificationEntity;
import com.uasz.alumni.notificationmessaging.repository.NotificationRepository;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationChannel;
import com.uasz.alumni.notificationmessaging.spi.model.NotificationType;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ALUM-139 contre un vrai PostgreSQL : chacun ne voit que ses notifications ; « tout lire » remet le compteur à 0. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationCenterService.class, NotificationCenterServiceIntegrationTest.Horloge.class})
class NotificationCenterServiceIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = demarrer();
    private static final Instant MAINTENANT = Instant.parse("2026-11-21T09:00:00Z");

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
            return Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        }
    }

    @Autowired NotificationCenterService center;
    @Autowired NotificationRepository notifications;

    private final UUID awa = UUID.randomUUID();
    private final UUID moussa = UUID.randomUUID();
    private NotificationEntity ancienne;
    private NotificationEntity recente;

    @BeforeEach
    void setUp() {
        ancienne = notifier(awa, "Ancienne", 2);
        recente = notifier(awa, "Récente", 1);
        notifier(moussa, "Pour Moussa", 1);
    }

    private NotificationEntity notifier(UUID userId, String title, int heuresAvant) {
        return notifications.saveAndFlush(new NotificationEntity(userId, NotificationType.APPLICATION_STATUS_CHANGED,
                NotificationChannel.IN_APP, title, "Contenu", null,
                OffsetDateTime.ofInstant(MAINTENANT.minusSeconds(3600L * heuresAvant), ZoneOffset.UTC)));
    }

    @Test
    void chacunNeVoitQueSesNotifications_lesPlusRecentesDAbord() {
        Page<NotificationEntity> page = center.list(awa, false, 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(NotificationEntity::getTitle).containsExactly("Récente", "Ancienne");
        assertThat(center.unreadCount(awa)).isEqualTo(2);
    }

    @Test
    void marquerCommeLue_puisFiltrerLesNonLues() {
        NotificationEntity lue = center.markRead(recente.getId(), awa);
        assertThat(lue.isRead()).isTrue();
        assertThat(lue.getReadAt()).isEqualTo(OffsetDateTime.ofInstant(MAINTENANT, ZoneOffset.UTC));

        assertThat(center.list(awa, true, 0, 20).getContent()).extracting(NotificationEntity::getId)
                .containsExactly(ancienne.getId());
        assertThat(center.unreadCount(awa)).isEqualTo(1);
    }

    @Test
    void laNotificationDUnAutreRepond404() {
        assertThatThrownBy(() -> center.markRead(recente.getId(), moussa)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(center.unreadCount(awa)).isEqualTo(2);
    }

    @Test
    void apresToutMarquerCommeLuLeCompteurVautZero_sansToucherAuxAutres() {
        center.markAllRead(awa);

        assertThat(center.unreadCount(awa)).isZero();
        assertThat(center.unreadCount(moussa)).isEqualTo(1);
    }

    @Test
    void unePaginationHorsBornesEstRameneeDansLesBornes() {
        Page<NotificationEntity> page = center.list(awa, null, -3, 500);
        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(100);
    }
}
