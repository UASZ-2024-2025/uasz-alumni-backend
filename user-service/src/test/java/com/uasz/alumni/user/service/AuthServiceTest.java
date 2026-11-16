
package com.uasz.alumni.user.service;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.account;
import static com.uasz.alumni.user.UserTestData.authProps;
import static com.uasz.alumni.user.UserTestData.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.exceptions.AccountDisabledException;
import com.uasz.alumni.user.exceptions.AccountLockedException;
import com.uasz.alumni.user.exceptions.AccountNotActivatedException;
import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.InvalidCredentialsException;
import com.uasz.alumni.user.exceptions.InvalidRefreshTokenException;
import com.uasz.alumni.user.jwt.JwtService;
import com.uasz.alumni.user.model.RefreshToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String EMAIL = "awa.diop@uasz.sn";

    @Mock UserAccountRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock RefreshTokenService refreshTokens;
    @Mock AuditService audit;

    private AuthService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(any(CharSequence.class))).thenReturn("DUMMY"); // hash factice calculé par le constructeur
        service = new AuthService(users, encoder, jwt, refreshTokens, audit, authProps(), clock());
    }

    private UserAccount activeUser() {
        return account(ID, EMAIL, RoleName.ALUMNI, UserStatus.ACTIF);
    }

    private void givenUser(UserAccount user) {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    // ───── login ─────

    @Test
    void login_success_returnsTokens_andRecordsLogin() {
        UserAccount user = activeUser();
        givenUser(user);
        when(encoder.matches("motdepasse1", "HASH")).thenReturn(true);
        when(refreshTokens.issue(user)).thenReturn("REFRESH");
        when(jwt.generateAccessToken(user)).thenReturn("ACCESS");
        when(jwt.accessTtlSeconds()).thenReturn(900L);

        AuthSession session = service.login("  Awa.Diop@UASZ.sn ", "motdepasse1");

        assertThat(session.accessToken()).isEqualTo("ACCESS");
        assertThat(session.refreshToken()).isEqualTo("REFRESH");
        assertThat(session.expiresInSeconds()).isEqualTo(900L);
        assertThat(session.user()).isSameAs(user);
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
        verify(audit).record("LOGIN", ID, ID, null);
    }

    @Test
    void login_success_resetsPreviousFailedAttempts() {
        UserAccount user = activeUser();
        user.registerFailedLogin(3, Duration.ofMinutes(15), NOW);
        givenUser(user);
        when(encoder.matches("motdepasse1", "HASH")).thenReturn(true);
        when(refreshTokens.issue(user)).thenReturn("REFRESH");
        when(jwt.generateAccessToken(user)).thenReturn("ACCESS");
        when(jwt.accessTtlSeconds()).thenReturn(900L);

        service.login(EMAIL, "motdepasse1");

        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void login_unknownEmail_sameErrorAsWrongPassword_andStillSpendsHashingTime() {
        when(users.findByEmail("inconnu@uasz.sn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("inconnu@uasz.sn", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(encoder).matches("whatever", "DUMMY"); // comparaison factice : pas d'écart de temps exploitable
        verify(audit).record("LOGIN_FAILED", null, null, "email=inconnu@uasz.sn");
        verifyNoInteractions(refreshTokens, jwt);
    }

    @Test
    void login_wrongPassword_incrementsCounter_andAudits() {
        UserAccount user = activeUser();
        givenUser(user);
        when(encoder.matches("bad", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "bad")).isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(audit).record("LOGIN_FAILED", ID, ID, null);
        verifyNoInteractions(refreshTokens, jwt);
    }

    @Test
    void login_locksAccountAfterMaxAttempts_thenRefusesWithoutCheckingPassword() {
        UserAccount user = activeUser();
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(encoder.matches("bad", "HASH")).thenReturn(false);

        for (int i = 0; i < 3; i++) { // authProps() : 3 échecs maximum
            assertThatThrownBy(() -> service.login(EMAIL, "bad")).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(user.isLockedAt(NOW)).isTrue();

        assertThatThrownBy(() -> service.login(EMAIL, "bad")).isInstanceOf(AccountLockedException.class);

        verify(encoder, times(3)).matches("bad", "HASH"); // pas de 4e vérification du mot de passe
        verify(audit).record("LOGIN_LOCKED", ID, ID, null);
    }

    @Test
    void login_lockedAccount_isRefusedEvenWithCorrectPassword() {
        UserAccount user = activeUser();
        user.registerFailedLogin(1, Duration.ofMinutes(15), NOW);
        givenUser(user);

        assertThatThrownBy(() -> service.login(EMAIL, "motdepasse1")).isInstanceOf(AccountLockedException.class);

        verify(encoder, never()).matches("motdepasse1", "HASH");
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void login_expiredLock_allowsLoginAgain() {
        UserAccount user = activeUser();
        user.registerFailedLogin(1, Duration.ofMinutes(15), NOW.minus(Duration.ofHours(1))); // verrou échu
        givenUser(user);
        when(encoder.matches("motdepasse1", "HASH")).thenReturn(true);
        when(refreshTokens.issue(user)).thenReturn("REFRESH");
        when(jwt.generateAccessToken(user)).thenReturn("ACCESS");
        when(jwt.accessTtlSeconds()).thenReturn(900L);

        AuthSession session = service.login(EMAIL, "motdepasse1");

        assertThat(session.accessToken()).isEqualTo("ACCESS");
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void login_pendingAccount_withCorrectPassword_isNotActivated() {
        givenUser(account(ID, EMAIL, RoleName.ALUMNI, UserStatus.EN_ATTENTE));
        when(encoder.matches("motdepasse1", "HASH")).thenReturn(true);

        assertThatThrownBy(() -> service.login(EMAIL, "motdepasse1")).isInstanceOf(AccountNotActivatedException.class);

        verifyNoInteractions(refreshTokens, jwt);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIF", "SUSPENDU"})
    void login_disabledAccount_withCorrectPassword_isRefused(UserStatus status) {
        givenUser(account(ID, EMAIL, RoleName.ALUMNI, status));
        when(encoder.matches("motdepasse1", "HASH")).thenReturn(true);

        assertThatThrownBy(() -> service.login(EMAIL, "motdepasse1")).isInstanceOf(AccountDisabledException.class);

        verifyNoInteractions(refreshTokens, jwt);
    }

    @Test
    void login_pendingAccount_withWrongPassword_doesNotRevealStatus() {
        givenUser(account(ID, EMAIL, RoleName.ALUMNI, UserStatus.EN_ATTENTE));
        when(encoder.matches("bad", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "bad")).isInstanceOf(InvalidCredentialsException.class);
    }

    // ───── refresh ─────

    private RefreshToken validToken(UserAccount user) {
        return new RefreshToken(user, "hash", NOW.minusSeconds(60), NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void refresh_rotatesToken() {
        UserAccount user = activeUser();
        RefreshToken token = validToken(user);
        when(refreshTokens.find("raw")).thenReturn(Optional.of(token));
        when(refreshTokens.issue(user)).thenReturn("NEW");
        when(jwt.generateAccessToken(user)).thenReturn("ACCESS2");
        when(jwt.accessTtlSeconds()).thenReturn(900L);

        AuthSession session = service.refresh("raw");

        assertThat(token.isRevoked()).isTrue(); // l'ancien est révoqué
        assertThat(session.refreshToken()).isEqualTo("NEW");
        assertThat(session.accessToken()).isEqualTo("ACCESS2");
        assertThat(session.user()).isSameAs(user);
        verify(audit).record("TOKEN_REFRESHED", ID, ID, null);
    }

    @Test
    void refresh_unknownToken_isRejected() {
        when(refreshTokens.find("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("inconnu")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokens, never()).issue(any(UserAccount.class));
    }

    @Test
    void refresh_reusedToken_revokesAllSessions() {
        UserAccount user = activeUser();
        RefreshToken token = validToken(user);
        token.revoke(NOW.minusSeconds(5)); // déjà utilisé une fois : sa réutilisation est suspecte
        when(refreshTokens.find("raw")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokens).revokeAll(ID);
        verify(audit).record("REFRESH_REUSE_DETECTED", ID, ID, null);
        verify(refreshTokens, never()).issue(any(UserAccount.class));
    }

    @Test
    void refresh_expiredToken_isRejected_includingAtExactExpiry() {
        UserAccount user = activeUser();
        RefreshToken token = new RefreshToken(user, "hash", NOW.minusSeconds(60), NOW); // expire à l'instant courant
        when(refreshTokens.find("raw")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(token.isRevoked()).isFalse();
        verify(refreshTokens, never()).issue(any(UserAccount.class));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"EN_ATTENTE", "INACTIF", "SUSPENDU"})
    void refresh_nonActiveUser_isRejected(UserStatus status) {
        UserAccount user = account(ID, EMAIL, RoleName.ALUMNI, status);
        when(refreshTokens.find("raw")).thenReturn(Optional.of(validToken(user)));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_lockedUser_isRejected() {
        UserAccount user = activeUser();
        user.registerFailedLogin(1, Duration.ofMinutes(15), NOW);
        when(refreshTokens.find("raw")).thenReturn(Optional.of(validToken(user)));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    // ───── logout ─────

    @Test
    void logout_revokesProvidedRefreshToken_andAudits() {
        service.logout(ID, "raw");

        verify(refreshTokens).revoke(ID, "raw");
        verify(audit).record("LOGOUT", ID, ID, null);
    }

    // ───── garde-fou transactionnel ─────

    @Test
    void loginAndRefresh_mustCommitEvenWhenAnApiExceptionIsThrown() throws Exception {
        // Sans noRollbackFor, le compteur d'échecs, le verrouillage et la révocation sur réutilisation
        // seraient annulés par le rollback déclenché par l'exception 401/403/423.
        Transactional login = AuthService.class.getMethod("login", String.class, String.class)
                .getAnnotation(Transactional.class);
        Transactional refresh = AuthService.class.getMethod("refresh", String.class)
                .getAnnotation(Transactional.class);

        assertThat(login.noRollbackFor()).contains(ApiException.class);
        assertThat(refresh.noRollbackFor()).contains(ApiException.class);
    }
}
