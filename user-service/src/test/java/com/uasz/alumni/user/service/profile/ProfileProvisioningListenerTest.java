package com.uasz.alumni.user.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import com.uasz.alumni.user.spi.model.RoleName;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ProfileProvisioningListenerTest {

    private static final UUID USER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Mock StudentCompanyClient client;

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
    void theEventDoesNotLogPersonalData() {
        assertThat(event(RoleName.ETUDIANT).toString())
                .contains(USER_ID.toString())
                .doesNotContain("awa.diop@uasz.sn", "Awa", "Diop");
    }
}
