
package com.uasz.alumni.user.service;

import static com.uasz.alumni.user.UserTestData.appProps;
import static com.uasz.alumni.user.UserTestData.apiError;
import static com.uasz.alumni.user.UserTestData.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.RoleNotAllowedException;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.EmailConfirmationTokenRepository;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RegisterRequest;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Règles ajoutées à l'inscription par AL-19/AL-20 : rôle ENTREPRISE et politique de mot de passe. */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceRulesTest {

    @Mock UserAccountRepository users;
    @Mock EmailConfirmationTokenRepository tokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher events;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(users, tokens, passwordEncoder, events, appProps(), clock());
    }

    @Test
    void register_entreprise_isAllowed_andStartsPending() {
        when(users.existsByEmail("rh@gainde.sn")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse1")).thenReturn("HASH");
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAccount created = service.register(
                new RegisterRequest("Diop", "Awa", "rh@gainde.sn", "motdepasse1", RoleName.ENTREPRISE));

        assertThat(created.getRole()).isEqualTo(RoleName.ENTREPRISE);
        assertThat(created.getStatus()).isEqualTo(UserStatus.EN_ATTENTE);
    }

    @Test
    void register_administrateur_isStillRefused() {
        assertThatThrownBy(() -> service.register(
                new RegisterRequest("Diop", "Awa", "a@uasz.sn", "motdepasse1", RoleName.ADMINISTRATEUR)))
                .isInstanceOf(RoleNotAllowedException.class);

        verifyNoInteractions(users, tokens, passwordEncoder, events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"court1", "sanschiffreici", "123456789"})
    void register_weakPassword_isRefusedWith400_beforeAnyDatabaseAccess(String password) {
        assertThatThrownBy(() -> service.register(
                new RegisterRequest("Diop", "Awa", "a@uasz.sn", password, RoleName.ALUMNI)))
                .isInstanceOfSatisfying(ApiException.class, apiError(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD"));

        verifyNoInteractions(users, tokens, passwordEncoder, events);
    }
}
