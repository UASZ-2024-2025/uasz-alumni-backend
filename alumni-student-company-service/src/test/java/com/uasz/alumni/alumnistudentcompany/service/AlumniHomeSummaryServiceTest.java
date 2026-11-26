package com.uasz.alumni.alumnistudentcompany.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.security.CurrentUserProvider;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniHomeSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** Synthèse d'accueil Alumni (ALUM-246) sans Spring : client de messagerie et appelant simulés. */
class AlumniHomeSummaryServiceTest {

    private final MessagingClient messaging = mock(MessagingClient.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final AlumniHomeSummaryService service = new AlumniHomeSummaryService(messaging, currentUser);

    @BeforeEach
    void caller() {
        when(currentUser.requireBearerToken()).thenReturn("jeton-alumni");
    }

    @Test
    void renvoieLesNonLusDeLaMessagerieAvecLeJetonDeLAppelant() {
        when(messaging.unreadMessages("jeton-alumni")).thenReturn(7);

        AlumniHomeSummary summary = service.summaryForCurrentUser();

        assertThat(summary.getUnreadMessages()).isEqualTo(7);
        verify(messaging).unreadMessages("jeton-alumni");
    }

    @Test
    void recommandationsEtDemandesSontAZeroFauteDeSource() {
        when(messaging.unreadMessages("jeton-alumni")).thenReturn(0);

        AlumniHomeSummary summary = service.summaryForCurrentUser();

        assertThat(summary.getRecommendationsGiven()).isZero();
        assertThat(summary.getPendingRequests()).isZero();
    }

    @Test
    void messagerieInjoignable_compteurAZeroSansErreur() {
        when(messaging.unreadMessages("jeton-alumni")).thenThrow(new ResourceAccessException("timeout"));

        assertThat(service.summaryForCurrentUser().getUnreadMessages()).isZero();
    }

    @Test
    void messagerieEnErreur_compteurAZeroSansErreur() {
        when(messaging.unreadMessages("jeton-alumni"))
                .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "indisponible",
                        null, null, null));

        assertThat(service.summaryForCurrentUser().getUnreadMessages()).isZero();
    }
}
