
package com.uasz.alumni.user.service;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.account;
import static com.uasz.alumni.user.UserTestData.apiError;
import static com.uasz.alumni.user.UserTestData.appProps;
import static com.uasz.alumni.user.UserTestData.authProps;
import static com.uasz.alumni.user.UserTestData.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.InvalidCurrentPasswordException;
import com.uasz.alumni.user.exceptions.InvalidResetTokenException;
import com.uasz.alumni.user.exceptions.UserNotFoundException;
import com.uasz.alumni.user.exceptions.WeakPasswordException;
import com.uasz.alumni.user.model.PasswordResetToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.PasswordResetTokenRepository;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String EMAIL = "awa.diop@uasz.sn";

    @Mock UserAccountRepository users;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock RefreshTokenService refreshTokens;
    @Mock PasswordEncoder encoder;
    @Mock ApplicationEventPublisher events;
    @Mock AuditService audit;

    private PasswordService service;

    @BeforeEach
    void setUp() {
        service = new PasswordService(users, resetTokens, refreshTokens, encoder, events,
                authProps(), appProps(), audit, clock());
    }

    private UserAccount user(UserStatus status) {
        return account(ID, EMAIL, RoleName.ALUMNI, status);
    }

    private PasswordResetToken tokenFor(UserAccount user, String raw, java.time.Instant expiresAt) {
        return new PasswordResetToken(user, TokenUtils.sha256Hex(raw), NOW.minusSeconds(60), expiresAt);
    }

    private void givenToken(String raw, PasswordResetToken token) {
        when(resetTokens.findByTokenHashWithUser(TokenUtils.sha256Hex(raw))).thenReturn(Optional.of(token));
    }

    private Object publishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }

    // ───── forgotPassword ─────

    @Test
    void forgotPassword_forActiveAccount_storesHashedToken_andPublishesResetEmail() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.ACTIF)));
        when(resetTokens.findFirstByUserIdOrderByCreatedAtDesc(ID)).thenReturn(Optional.empty());

        service.forgotPassword("  Awa.Diop@UASZ.sn ");

        verify(resetTokens).deleteAllByUserId(ID); // un seul jeton actif par utilisateur
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(saved.capture());
        PasswordEmailRequested event = (PasswordEmailRequested) publishedEvent();

        assertThat(event.kind()).isEqualTo(PasswordEmailRequested.Kind.RESET);
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.firstName()).isEqualTo("Awa");
        assertThat(event.ttl()).isEqualTo(Duration.ofHours(1));
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenUtils.sha256Hex(event.token())).isNotEqualTo(event.token());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        verify(audit).record("PASSWORD_RESET_REQUESTED", ID, ID, null);
    }

    @Test
    void forgotPassword_forPendingAccount_isAllowed() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.EN_ATTENTE)));
        when(resetTokens.findFirstByUserIdOrderByCreatedAtDesc(ID)).thenReturn(Optional.empty());

        service.forgotPassword(EMAIL);

        verify(resetTokens).save(any(PasswordResetToken.class));
    }

    @Test
    void forgotPassword_silentlyIgnoresUnknownEmail() {
        when(users.findByEmail("inconnu@uasz.sn")).thenReturn(Optional.empty());

        service.forgotPassword("inconnu@uasz.sn");

        verifyNoInteractions(resetTokens, events, audit);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIF", "SUSPENDU"})
    void forgotPassword_silentlyIgnoresDisabledAccounts(UserStatus status) {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(status)));

        service.forgotPassword(EMAIL);

        verifyNoInteractions(resetTokens, events, audit);
    }

    @Test
    void forgotPassword_withinCooldown_isSilentlyIgnored() {
        UserAccount user = user(UserStatus.ACTIF);
        PasswordResetToken recent = new PasswordResetToken(user, "h", NOW.minusSeconds(30), NOW.plusSeconds(3000));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(resetTokens.findFirstByUserIdOrderByCreatedAtDesc(ID)).thenReturn(Optional.of(recent));

        service.forgotPassword(EMAIL);

        verify(resetTokens, never()).save(any(PasswordResetToken.class));
        verifyNoInteractions(events);
    }

    @Test
    void forgotPassword_afterCooldown_issuesNewToken() {
        UserAccount user = user(UserStatus.ACTIF);
        PasswordResetToken old = new PasswordResetToken(user, "h", NOW.minusSeconds(60), NOW.plusSeconds(3000));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(resetTokens.findFirstByUserIdOrderByCreatedAtDesc(ID)).thenReturn(Optional.of(old));

        service.forgotPassword(EMAIL);

        verify(resetTokens).save(any(PasswordResetToken.class));
    }

    // ───── sendInvitation ─────

    @Test
    void sendInvitation_publishesInvitationEmailWithConfirmationTtl() {
        UserAccount user = user(UserStatus.EN_ATTENTE);

        service.sendInvitation(user);

        PasswordEmailRequested event = (PasswordEmailRequested) publishedEvent();
        assertThat(event.kind()).isEqualTo(PasswordEmailRequested.Kind.INVITATION);
        assertThat(event.ttl()).isEqualTo(Duration.ofHours(24));
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        verify(resetTokens).deleteAllByUserId(ID);
    }

    // ───── resetPassword ─────

    @Test
    void resetPassword_setsNewHash_consumesToken_revokesSessions_andNotifies() {
        UserAccount user = user(UserStatus.ACTIF);
        PasswordResetToken token = tokenFor(user, "tok", NOW.plusSeconds(600));
        givenToken("tok", token);
        when(encoder.encode("Nouveau123")).thenReturn("NEWHASH");

        service.resetPassword("  tok ", "Nouveau123");

        assertThat(user.getPasswordHash()).isEqualTo("NEWHASH");
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokens).revokeAll(ID);
        assertThat(publishedEvent()).isEqualTo(new PasswordChangedNotice(EMAIL, "Awa"));
        verify(audit).record("PASSWORD_RESET", ID, ID, null);
    }

    @Test
    void resetPassword_activatesPendingAccount() {
        UserAccount user = user(UserStatus.EN_ATTENTE);
        givenToken("tok", tokenFor(user, "tok", NOW.plusSeconds(600)));
        when(encoder.encode("Nouveau123")).thenReturn("NEWHASH");

        service.resetPassword("tok", "Nouveau123");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIF);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void resetPassword_liftsAccountLock() {
        UserAccount user = user(UserStatus.ACTIF);
        user.registerFailedLogin(1, Duration.ofMinutes(15), NOW);
        givenToken("tok", tokenFor(user, "tok", NOW.plusSeconds(600)));
        when(encoder.encode("Nouveau123")).thenReturn("NEWHASH");

        service.resetPassword("tok", "Nouveau123");

        assertThat(user.isLockedAt(NOW)).isFalse();
    }

    @Test
    void resetPassword_unknownToken_isRejected() {
        when(resetTokens.findByTokenHashWithUser(TokenUtils.sha256Hex("inconnu"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("inconnu", "Nouveau123"))
                .isInstanceOf(InvalidResetTokenException.class);

        verifyNoInteractions(encoder, refreshTokens, events, audit);
    }

    @Test
    void resetPassword_usedToken_isRejected() {
        UserAccount user = user(UserStatus.ACTIF);
        PasswordResetToken token = tokenFor(user, "tok", NOW.plusSeconds(600));
        token.markUsed(NOW.minusSeconds(10));
        givenToken("tok", token);

        assertThatThrownBy(() -> service.resetPassword("tok", "Nouveau123"))
                .isInstanceOf(InvalidResetTokenException.class);

        assertThat(user.getPasswordHash()).isEqualTo("HASH");
    }

    @Test
    void resetPassword_expiredToken_isRejected_includingAtExactExpiry() {
        UserAccount user = user(UserStatus.ACTIF);
        givenToken("tok", tokenFor(user, "tok", NOW));

        assertThatThrownBy(() -> service.resetPassword("tok", "Nouveau123"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIF", "SUSPENDU"})
    void resetPassword_disabledAccount_isRejected(UserStatus status) {
        UserAccount user = user(status);
        givenToken("tok", tokenFor(user, "tok", NOW.plusSeconds(600)));

        assertThatThrownBy(() -> service.resetPassword("tok", "Nouveau123"))
                .isInstanceOf(InvalidResetTokenException.class);

        assertThat(user.getPasswordHash()).isEqualTo("HASH");
    }

    @Test
    void resetPassword_weakPassword_returns422_withoutConsumingToken() {
        UserAccount user = user(UserStatus.ACTIF);
        PasswordResetToken token = tokenFor(user, "tok", NOW.plusSeconds(600));
        givenToken("tok", token);

        assertThatThrownBy(() -> service.resetPassword("tok", "court1"))
                .isInstanceOfSatisfying(WeakPasswordException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(422);
                    assertThat(e.getCode()).isEqualTo("WEAK_PASSWORD");
                });

        assertThat(token.isUsed()).isFalse(); // l'utilisateur peut réessayer avec le même lien
        verify(encoder, never()).encode(any(CharSequence.class));
        verifyNoInteractions(refreshTokens, events);
    }

    @Test
    void resetPassword_invalidToken_takesPrecedenceOverWeakPassword() {
        when(resetTokens.findByTokenHashWithUser(TokenUtils.sha256Hex("inconnu"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("inconnu", "faible"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    // ───── changePassword ─────

    private void givenUserWithCurrentPassword(UserAccount user) {
        when(users.findById(ID)).thenReturn(Optional.of(user));
        when(encoder.matches("Actuel123", "HASH")).thenReturn(true);
    }

    @Test
    void changePassword_success_revokesAllSessions_andNotifies() {
        UserAccount user = user(UserStatus.ACTIF);
        givenUserWithCurrentPassword(user);
        when(encoder.matches("Nouveau123", "HASH")).thenReturn(false);
        when(encoder.encode("Nouveau123")).thenReturn("NEWHASH");

        service.changePassword(ID, "Actuel123", "Nouveau123");

        assertThat(user.getPasswordHash()).isEqualTo("NEWHASH");
        verify(refreshTokens).revokeAll(ID);
        assertThat(publishedEvent()).isEqualTo(new PasswordChangedNotice(EMAIL, "Awa"));
        verify(audit).record("PASSWORD_CHANGED", ID, ID, null);
    }

    @Test
    void changePassword_wrongCurrentPassword_isRefused() {
        UserAccount user = user(UserStatus.ACTIF);
        when(users.findById(ID)).thenReturn(Optional.of(user));
        when(encoder.matches("faux", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(ID, "faux", "Nouveau123"))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        assertThat(user.getPasswordHash()).isEqualTo("HASH");
        verifyNoInteractions(refreshTokens, events);
    }

    @Test
    void changePassword_weakNewPassword_returns422() {
        givenUserWithCurrentPassword(user(UserStatus.ACTIF));

        assertThatThrownBy(() -> service.changePassword(ID, "Actuel123", "court1"))
                .isInstanceOfSatisfying(WeakPasswordException.class, e -> assertThat(e.getStatus().value()).isEqualTo(422));

        verifyNoInteractions(refreshTokens, events);
    }

    @Test
    void changePassword_sameAsCurrent_isRefused() {
        givenUserWithCurrentPassword(user(UserStatus.ACTIF));

        assertThatThrownBy(() -> service.changePassword(ID, "Actuel123", "Actuel123"))
                .isInstanceOfSatisfying(ApiException.class, apiError(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED"));

        verifyNoInteractions(refreshTokens, events);
    }

    @Test
    void changePassword_unknownUser_throwsNotFound() {
        when(users.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(ID, "Actuel123", "Nouveau123"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
