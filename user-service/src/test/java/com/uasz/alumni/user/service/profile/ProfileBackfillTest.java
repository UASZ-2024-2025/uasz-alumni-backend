package com.uasz.alumni.user.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ProfileBackfillTest {

    @Mock UserAccountRepository users;
    @Mock StudentCompanyClient client;

    private ProfileBackfill backfill(boolean enabled) {
        return new ProfileBackfill(users, client, Runnable::run, enabled);
    }

    private static UserAccount account(RoleName role, String first) {
        UserAccount a = mock(UserAccount.class);
        // lenient : un rattrapage interrompu ne lit pas les comptes suivants
        lenient().when(a.getId()).thenReturn(UUID.randomUUID());
        lenient().when(a.getFirstName()).thenReturn(first);
        lenient().when(a.getLastName()).thenReturn("Diop");
        lenient().when(a.getEmail()).thenReturn(first.toLowerCase() + ".diop@zig.univ.sn");
        return a;
    }

    private void accounts(RoleName role, List<UserAccount> content) {
        when(users.findByRoleAndStatus(eq(role), eq(UserStatus.ACTIF), any(Pageable.class)))
                .thenReturn(new PageImpl<>(content, PageRequest.of(0, ProfileBackfill.PAGE_SIZE), content.size()));
    }

    @Test
    void sendsProvisioningForEveryActiveStudentAndAlumni() {
        UserAccount awa = account(RoleName.ETUDIANT, "Awa");
        UserAccount moussa = account(RoleName.ALUMNI, "Moussa");
        accounts(RoleName.ETUDIANT, List.of(awa));
        accounts(RoleName.ALUMNI, List.of(moussa));

        assertThat(backfill(true).backfill()).isZero();

        ArgumentCaptor<ProfileProvisioningRequest> sent = ArgumentCaptor.forClass(ProfileProvisioningRequest.class);
        verify(client, times(2)).provisionProfile(sent.capture());
        assertThat(sent.getAllValues()).extracting(ProfileProvisioningRequest::getUserId,
                        ProfileProvisioningRequest::getProfileType, ProfileProvisioningRequest::getFirstName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(awa.getId(), ProvisionedProfileType.ETUDIANT, "Awa"),
                        org.assertj.core.groups.Tuple.tuple(moussa.getId(), ProvisionedProfileType.ALUMNI, "Moussa"));
    }

    @Test
    void readsEveryPage() {
        UserAccount first = account(RoleName.ETUDIANT, "Awa");
        UserAccount second = account(RoleName.ETUDIANT, "Fatou");
        Page<UserAccount> page0 = new PageImpl<>(List.of(first), PageRequest.of(0, 1), 2);
        Page<UserAccount> page1 = new PageImpl<>(List.of(second), PageRequest.of(1, 1), 2);
        when(users.findByRoleAndStatus(eq(RoleName.ETUDIANT), eq(UserStatus.ACTIF), any(Pageable.class)))
                .thenReturn(page0, page1);
        accounts(RoleName.ALUMNI, List.of());

        backfill(true).backfill();

        verify(client, times(2)).provisionProfile(any());
    }

    @Test
    void aRefusedAccountDoesNotStopTheOthers() {
        accounts(RoleName.ETUDIANT, List.of(account(RoleName.ETUDIANT, "Awa"), account(RoleName.ETUDIANT, "Fatou")));
        accounts(RoleName.ALUMNI, List.of());
        doThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad", null, null, null))
                .doNothing().when(client).provisionProfile(any());

        assertThat(backfill(true).backfill()).isEqualTo(1);
        verify(client, times(2)).provisionProfile(any());
    }

    @Test
    void unreachableServiceStopsTheBackfill() {
        accounts(RoleName.ETUDIANT, List.of(account(RoleName.ETUDIANT, "Awa"), account(RoleName.ETUDIANT, "Fatou")));
        doThrow(new ResourceAccessException("Connection refused")).when(client).provisionProfile(any());

        assertThat(backfill(true).backfill()).isEqualTo(1);
        verify(client, times(1)).provisionProfile(any());
    }

    @Test
    void disabledBackfillDoesNothing() {
        backfill(false).run(null);

        verifyNoInteractions(users, client);
    }
}
