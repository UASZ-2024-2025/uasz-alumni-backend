package com.uasz.alumni.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.config.AppProperties;
import com.uasz.alumni.user.exceptions.AccountAlreadyActiveException;
import com.uasz.alumni.user.exceptions.EmailAlreadyUsedException;
import com.uasz.alumni.user.exceptions.InvalidOrExpiredTokenException;
import com.uasz.alumni.user.exceptions.RoleNotAllowedException;
import com.uasz.alumni.user.model.EmailConfirmationToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.EmailConfirmationTokenRepository;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RegisterRequest;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T20:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);

    @Mock UserAccountRepository users;
    @Mock EmailConfirmationTokenRepository tokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher events;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties("http://localhost:4200", "no-reply@alumni-uasz.sn", TTL);
        service = new RegistrationService(users, tokens, passwordEncoder, events, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ───── register ─────

    @Test
    void register_createsPendingAccount_hashesPassword_andSendsHashedToken() {
        when(users.existsByEmail("awa.diop@uasz.sn")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse1")).thenReturn("HASH");
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAccount created = service.register(request("  Awa.Diop@UASZ.sn ", RoleName.ALUMNI));

        assertThat(created.getEmail()).isEqualTo("awa.diop@uasz.sn");
        assertThat(created.getPasswordHash()).isEqualTo("HASH");
        assertThat(created.getStatus()).isEqualTo(UserStatus.EN_ATTENTE);
        assertThat(created.getRole()).isEqualTo(RoleName.ALUMNI);

        ArgumentCaptor<EmailConfirmationToken> savedToken = ArgumentCaptor.forClass(EmailConfirmationToken.class);
        verify(tokens).save(savedToken.capture());
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());

        ConfirmationEmailRequested event = (ConfirmationEmailRequested) published.getValue();
        assertThat(event.email()).isEqualTo("awa.diop@uasz.sn");
        // la base ne contient que l'empreinte, jamais le jeton envoyé par email
        assertThat(savedToken.getValue().getTokenHash())
                .isNotEqualTo(event.token())
                .isEqualTo(TokenUtils.sha256Hex(event.token()));
        assertThat(savedToken.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void register_refusesAdministratorRole() {
        assertThatThrownBy(() -> service.register(request("root@uasz.sn", RoleName.ADMINISTRATEUR)))
                .isInstanceOf(RoleNotAllowedException.class);
        verifyNoInteractions(users, tokens, events);
    }

    @Test
    void register_refusesDuplicateEmail() {
        when(users.existsByEmail("awa.diop@uasz.sn")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request("awa.diop@uasz.sn", RoleName.ETUDIANT)))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void register_translatesUniqueConstraintViolationIntoConflict() {
        when(users.existsByEmail("awa.diop@uasz.sn")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse1")).thenReturn("HASH");
        when(users.saveAndFlush(any(UserAccount.class))).thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> service.register(request("awa.diop@uasz.sn", RoleName.ENSEIGNANT)))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verifyNoInteractions(events);
    }

    // ───── confirmEmail ─────

    @Test
    void confirmEmail_activatesAccountAndConsumesToken() {
        UserAccount account = account(UserStatus.EN_ATTENTE);
        EmailConfirmationToken token = tokenFor(account, "raw-token", NOW.plus(Duration.ofHours(1)));
        when(tokens.findByTokenHashWithUser(TokenUtils.sha256Hex("raw-token"))).thenReturn(Optional.of(token));

        service.confirmEmail("raw-token");

        assertThat(account.getStatus()).isEqualTo(UserStatus.ACTIF);
        assertThat(account.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void confirmEmail_rejectsUnknownToken() {
        when(tokens.findByTokenHashWithUser(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmEmail("inconnu"))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void confirmEmail_rejectsExpiredToken() {
        UserAccount account = account(UserStatus.EN_ATTENTE);
        EmailConfirmationToken token = tokenFor(account, "old", NOW.minusSeconds(1));
        when(tokens.findByTokenHashWithUser(TokenUtils.sha256Hex("old"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirmEmail("old"))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
        assertThat(account.getStatus()).isEqualTo(UserStatus.EN_ATTENTE);
    }

    @Test
    void confirmEmail_rejectsAlreadyUsedToken() {
        UserAccount account = account(UserStatus.EN_ATTENTE);
        EmailConfirmationToken token = tokenFor(account, "used", NOW.plus(Duration.ofHours(1)));
        token.markUsed(NOW.minusSeconds(60));
        when(tokens.findByTokenHashWithUser(TokenUtils.sha256Hex("used"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirmEmail("used"))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void confirmEmail_doesNotReactivateSuspendedAccount() {
        UserAccount account = account(UserStatus.SUSPENDU);
        EmailConfirmationToken token = tokenFor(account, "tok", NOW.plus(Duration.ofHours(1)));
        when(tokens.findByTokenHashWithUser(TokenUtils.sha256Hex("tok"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirmEmail("tok"))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
        assertThat(account.getStatus()).isEqualTo(UserStatus.SUSPENDU);
    }

    // ───── resendConfirmation ─────

    @Test
    void resendConfirmation_issuesNewTokenForPendingAccount() {
        when(users.findByEmail("awa.diop@uasz.sn")).thenReturn(Optional.of(account(UserStatus.EN_ATTENTE)));

        service.resendConfirmation(" Awa.Diop@uasz.sn");

        verify(tokens).deleteAllByUserId(any());
        verify(tokens).save(any(EmailConfirmationToken.class));
        verify(events).publishEvent(any(Object.class));
    }

    @Test
    void resendConfirmation_reportsAlreadyActiveAccount() {
        when(users.findByEmail("awa.diop@uasz.sn")).thenReturn(Optional.of(account(UserStatus.ACTIF)));

        assertThatThrownBy(() -> service.resendConfirmation("awa.diop@uasz.sn"))
                .isInstanceOf(AccountAlreadyActiveException.class);
        verifyNoInteractions(tokens, events);
    }

    @Test
    void resendConfirmation_silentlyIgnoresUnknownEmail() {
        when(users.findByEmail("inconnu@uasz.sn")).thenReturn(Optional.empty());

        service.resendConfirmation("inconnu@uasz.sn");

        verifyNoInteractions(tokens, events);
    }

    @Test
    void resendConfirmation_silentlyIgnoresSuspendedAccount() {
        when(users.findByEmail("awa.diop@uasz.sn")).thenReturn(Optional.of(account(UserStatus.SUSPENDU)));

        service.resendConfirmation("awa.diop@uasz.sn");

        verifyNoInteractions(tokens, events);
    }

    // ───── helpers ─────

    private static RegisterRequest request(String email, RoleName role) {
        return new RegisterRequest("Diop", "Awa", email, "motdepasse1", role);
    }

    private static UserAccount account(UserStatus status) {
        return new UserAccount("awa.diop@uasz.sn", "HASH", "Awa", "Diop", RoleName.ALUMNI, status);
    }

    private static EmailConfirmationToken tokenFor(UserAccount account, String rawToken, Instant expiresAt) {
        return new EmailConfirmationToken(account, TokenUtils.sha256Hex(rawToken), NOW.minus(Duration.ofHours(1)), expiresAt);
    }
}
