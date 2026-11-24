package com.uasz.alumni.user.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import com.uasz.alumni.user.spi.model.RoleName;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ProfileProvisioningListenerTest {

    private static final UUID USER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Mock StudentCompanyClient client;

    private final Logger logger = (Logger) LoggerFactory.getLogger(ProfileProvisioningListener.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    private Level levelOfTheOnlyLog() {
        assertThat(logs.list).hasSize(1);
        return logs.list.get(0).getLevel();
    }

    private static ProfileProvisioningRequested event(RoleName role) {
        return new ProfileProvisioningRequested(USER_ID, role, "Awa", "Diop", "awa.diop@uasz.sn");
    }

    @Test
    void createsAStudentProfileForAnEtudiantAccount() {
        new ProfileProvisioningListener(client).onProfileProvisioningRequested(event(RoleName.ETUDIANT));

        ArgumentCaptor<ProfileProvisioningRequest> sent = ArgumentCaptor.forClass(ProfileProvisioningRequest.class);
        verify(client).provisionProfile(sent.capture());
        assertThat(sent.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(sent.getValue().getProfileType()).isEqualTo(ProvisionedProfileType.ETUDIANT);
        assertThat(sent.getValue().getFirstName()).isEqualTo("Awa");
        assertThat(sent.getValue().getLastName()).isEqualTo("Diop");
        assertThat(sent.getValue().getEmail()).isEqualTo("awa.diop@uasz.sn");
    }

    @Test
    void createsAnAlumniProfileForAnAlumniAccount() {
        new ProfileProvisioningListener(client).onProfileProvisioningRequested(event(RoleName.ALUMNI));

        ArgumentCaptor<ProfileProvisioningRequest> sent = ArgumentCaptor.forClass(ProfileProvisioningRequest.class);
        verify(client).provisionProfile(sent.capture());
        assertThat(sent.getValue().getProfileType()).isEqualTo(ProvisionedProfileType.ALUMNI);
    }

    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"ENSEIGNANT", "ENTREPRISE", "ADMINISTRATEUR"})
    void otherRolesHaveNoProfileInThisService(RoleName role) {
        new ProfileProvisioningListener(client).onProfileProvisioningRequested(event(role));

        verifyNoInteractions(client);
    }

    @Test
    void aFailureOfTheRemoteServiceNeverPropagates() {
        // retries épuisés : l'activation du compte, déjà validée, ne doit pas être remise en cause
        doThrow(new ResourceAccessException("connexion refusée")).when(client).provisionProfile(any());

        assertThatCode(() -> new ProfileProvisioningListener(client)
                .onProfileProvisioningRequested(event(RoleName.ETUDIANT)))
                .doesNotThrowAnyException();
    }

    @Test
    void aRealFailureIsLoggedAsAnError() {
        doThrow(new ResourceAccessException("connexion refusée")).when(client).provisionProfile(any());

        new ProfileProvisioningListener(client).onProfileProvisioningRequested(event(RoleName.ALUMNI));

        assertThat(levelOfTheOnlyLog()).isEqualTo(Level.ERROR);
    }

    @Test
    void aStudentProfileNotYetSupportedIs501AndOnlyAWarning() {
        // ALUM-214 : tant qu'ALUM-92 n'est pas fusionné, le service répond 501 pour un profil ETUDIANT
        doThrow(HttpServerErrorException.create(HttpStatus.NOT_IMPLEMENTED, "Not Implemented", null, null, null))
                .when(client).provisionProfile(any());

        assertThatCode(() -> new ProfileProvisioningListener(client)
                .onProfileProvisioningRequested(event(RoleName.ETUDIANT)))
                .doesNotThrowAnyException();

        assertThat(levelOfTheOnlyLog()).isEqualTo(Level.WARN);
        assertThat(logs.list.get(0).getFormattedMessage())
                .contains("501", "ETUDIANT", USER_ID.toString(), "ALUM-214")
                .doesNotContain("awa.diop@uasz.sn");
    }

    @Test
    void theEventDoesNotLogPersonalData() {
        assertThat(event(RoleName.ETUDIANT).toString())
                .contains(USER_ID.toString())
                .doesNotContain("awa.diop@uasz.sn", "Awa", "Diop");
    }
}
