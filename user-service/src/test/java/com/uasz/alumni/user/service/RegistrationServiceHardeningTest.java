
package com.uasz.alumni.user.service;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.appProps;
import static com.uasz.alumni.user.UserTestData.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.exceptions.InvalidRegistrationDataException;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.EmailConfirmationTokenRepository;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RegisterRequest;
import com.uasz.alumni.user.spi.model.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Comportements de la version durcie de RegistrationService (validation des champs, horodatage par l'horloge
 * injectée) qui cohabitent avec la politique de mot de passe d'AL-19/AL-20.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceHardeningTest {

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
    void register_stampsCreationDateFromInjectedClock() {
        when(users.existsByEmail("awa@uasz.sn")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse1")).thenReturn("HASH");
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAccount created = service.register(
                new RegisterRequest("Diop", "Awa", "Awa@UASZ.sn ", "motdepasse1", RoleName.ALUMNI));

        assertThat(created.getCreatedAt()).isEqualTo(NOW);
        assertThat(created.getEmail()).isEqualTo("awa@uasz.sn");
    }

    @Test
    void register_nullRequest_isABusinessError_notANullPointerException() {
        assertThatThrownBy(() -> service.register(null)).isInstanceOf(InvalidRegistrationDataException.class);

        verifyNoInteractions(users, tokens, passwordEncoder, events);
    }

    @Test
    void register_emptyPassword_isABusinessError() {
        assertThatThrownBy(() -> service.register(
                new RegisterRequest("Diop", "Awa", "awa@uasz.sn", "", RoleName.ALUMNI)))
                .isInstanceOf(InvalidRegistrationDataException.class);

        verifyNoInteractions(users, tokens, passwordEncoder, events);
    }

    @Test
    void register_missingRole_isABusinessError() {
        assertThatThrownBy(() -> service.register(
                new RegisterRequest("Diop", "Awa", "awa@uasz.sn", "motdepasse1", null)))
                .isInstanceOf(InvalidRegistrationDataException.class);

        verifyNoInteractions(users, tokens, passwordEncoder, events);
    }
}
