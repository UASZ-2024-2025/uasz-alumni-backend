
package com.uasz.alumni.user.config;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.account;
import static com.uasz.alumni.user.UserTestData.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.service.AuditService;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock UserAccountRepository users;
    @Mock PasswordEncoder encoder;
    @Mock AuditService audit;

    private AdminBootstrap bootstrap(String email, String password) {
        AuthProperties props = new AuthProperties(Duration.ofDays(7), Duration.ofHours(1), 5, Duration.ofMinutes(15),
                new AuthProperties.BootstrapAdmin(email, password, " Admin ", " Alumni UASZ "));
        return new AdminBootstrap(props, users, encoder, audit, clock());
    }

    @Test
    void createsActiveVerifiedAdministrator_whenNoneExists() {
        when(users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF)).thenReturn(0L);
        when(encoder.encode("Secret123abc")).thenReturn("HASH");

        bootstrap("  Admin@Alumni-UASZ.sn ", "Secret123abc").run(null);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).saveAndFlush(saved.capture());
        UserAccount admin = saved.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@alumni-uasz.sn");
        assertThat(admin.getPasswordHash()).isEqualTo("HASH");
        assertThat(admin.getRole()).isEqualTo(RoleName.ADMINISTRATEUR);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIF);
        assertThat(admin.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThat(admin.getFirstName()).isEqualTo("Admin");
        assertThat(admin.getLastName()).isEqualTo("Alumni UASZ");
        verify(audit).record(org.mockito.ArgumentMatchers.eq("ADMIN_BOOTSTRAPPED"), any(), any(), any());
    }

    @Test
    void doesNothing_whenAnActiveAdministratorAlreadyExists() {
        when(users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF)).thenReturn(1L);

        bootstrap("admin@alumni-uasz.sn", "Secret123abc").run(null);

        verify(users, never()).saveAndFlush(any(UserAccount.class));
        verifyNoInteractions(encoder, audit);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'',Secret123abc",              // email vide
            "' ',Secret123abc",             // email blanc
            "admin@alumni-uasz.sn,''",      // mot de passe vide
            "admin@alumni-uasz.sn,CHANGE_ME",
            "admin@alumni-uasz.sn,CHANGE_ME_123",
            "admin@alumni-uasz.sn,court1",  // trop court
            "admin@alumni-uasz.sn,sanschiffreici"
    })
    void isSkipped_whenConfigurationIsMissingOrUnsafe(String email, String password) {
        bootstrap(email, password).run(null);

        verifyNoInteractions(users, encoder, audit);
    }

    @Test
    void isSkipped_whenBootstrapSectionIsAbsent() {
        AuthProperties props = new AuthProperties(Duration.ofDays(7), Duration.ofHours(1), 5, Duration.ofMinutes(15), null);

        new AdminBootstrap(props, users, encoder, audit, clock()).run(null);

        verifyNoInteractions(users, encoder, audit);
    }

    @Test
    void concurrentCreationByAnotherReplica_isTolerated() {
        when(users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF)).thenReturn(0L);
        when(encoder.encode("Secret123abc")).thenReturn("HASH");
        when(users.saveAndFlush(any(UserAccount.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatCode(() -> bootstrap("admin@alumni-uasz.sn", "Secret123abc").run(null)).doesNotThrowAnyException();

        verifyNoInteractions(audit);
    }

    @Test
    void existingInactiveAdministrator_doesNotPreventBootstrapAttempt() {
        // un admin INACTIF ne compte pas comme "admin actif" : le bootstrap tente de créer le compte
        when(users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF)).thenReturn(0L);
        when(encoder.encode("Secret123abc")).thenReturn("HASH");

        bootstrap("admin@alumni-uasz.sn", "Secret123abc").run(null);

        verify(users).saveAndFlush(any(UserAccount.class));
    }

    @ParameterizedTest
    @CsvSource(value = {"'',Alumni", "' ',Alumni", "Admin,''", "Admin,' '"})
    void isSkipped_whenNamesAreBlank(String firstName, String lastName) {
        AuthProperties props = new AuthProperties(Duration.ofDays(7), Duration.ofHours(1), 5, Duration.ofMinutes(15),
                new AuthProperties.BootstrapAdmin("admin@alumni-uasz.sn", "Secret123abc", firstName, lastName));

        new AdminBootstrap(props, users, encoder, audit, clock()).run(null);

        verifyNoInteractions(users, encoder, audit);
    }
}
