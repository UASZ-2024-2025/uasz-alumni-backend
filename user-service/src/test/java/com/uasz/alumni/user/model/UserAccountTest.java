
package com.uasz.alumni.user.model;

import static com.uasz.alumni.user.UserTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.uasz.alumni.user.spi.model.Gender;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    private static final Duration LOCK = Duration.ofMinutes(15);

    private static UserAccount newAccount() {
        return new UserAccount("awa.diop@uasz.sn", "HASH", "Awa", "Diop", RoleName.ALUMNI, UserStatus.ACTIF);
    }

    // ───── verrouillage ─────

    @Test
    void failedLogins_belowThreshold_doNotLock() {
        UserAccount account = newAccount();

        account.registerFailedLogin(3, LOCK, NOW);
        account.registerFailedLogin(3, LOCK, NOW);

        assertThat(account.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(account.isLockedAt(NOW)).isFalse();
    }

    @Test
    void failedLogin_reachingThreshold_locksAndResetsCounter() {
        UserAccount account = newAccount();

        for (int i = 0; i < 3; i++) {
            account.registerFailedLogin(3, LOCK, NOW);
        }

        assertThat(account.getLockedUntil()).isEqualTo(NOW.plus(LOCK));
        assertThat(account.getFailedLoginAttempts()).isZero();
        assertThat(account.isLockedAt(NOW)).isTrue();
        assertThat(account.isLockedAt(NOW.plus(Duration.ofMinutes(14)))).isTrue();
    }

    @Test
    void lock_expiresAtLockedUntil() {
        UserAccount account = newAccount();
        account.registerFailedLogin(1, LOCK, NOW);

        assertThat(account.isLockedAt(NOW.plus(LOCK))).isFalse();
        assertThat(account.isLockedAt(NOW.plus(LOCK).plusSeconds(1))).isFalse();
    }

    @Test
    void successfulLogin_clearsCountersAndRecordsDate() {
        UserAccount account = newAccount();
        account.registerFailedLogin(3, LOCK, NOW);

        account.registerSuccessfulLogin(NOW.plusSeconds(5));

        assertThat(account.getFailedLoginAttempts()).isZero();
        assertThat(account.getLockedUntil()).isNull();
        assertThat(account.getLastLoginAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void clearLock_unlocksAccount() {
        UserAccount account = newAccount();
        account.registerFailedLogin(1, LOCK, NOW);
        assertThat(account.isLockedAt(NOW)).isTrue();

        account.clearLock();

        assertThat(account.isLockedAt(NOW)).isFalse();
        assertThat(account.getFailedLoginAttempts()).isZero();
    }

    // ───── cycle de vie ─────

    @Test
    void activate_setsActifAndEmailVerificationDate() {
        UserAccount account = new UserAccount("a@uasz.sn", "HASH", "A", "B", RoleName.ETUDIANT, UserStatus.EN_ATTENTE);

        account.activate(NOW);

        assertThat(account.getStatus()).isEqualTo(UserStatus.ACTIF);
        assertThat(account.getEmailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void simpleMutators_changeState() {
        UserAccount account = newAccount();

        account.changeStatus(UserStatus.SUSPENDU);
        account.changePasswordHash("NEWHASH");
        account.changeEmail("nouveau@uasz.sn");

        assertThat(account.getStatus()).isEqualTo(UserStatus.SUSPENDU);
        assertThat(account.getPasswordHash()).isEqualTo("NEWHASH");
        assertThat(account.getEmail()).isEqualTo("nouveau@uasz.sn");
    }

    // ───── mise à jour partielle du profil ─────

    @Test
    void updateProfile_ignoresNullArguments() {
        UserAccount account = newAccount();
        account.updateProfile(null, null, "+221770000000", Gender.FEMALE, LocalDate.of(1998, 5, 17));

        account.updateProfile("Aïcha", null, null, null, null);

        assertThat(account.getFirstName()).isEqualTo("Aïcha");
        assertThat(account.getLastName()).isEqualTo("Diop");
        assertThat(account.getPhone()).isEqualTo("+221770000000");
        assertThat(account.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(account.getBirthDate()).isEqualTo(LocalDate.of(1998, 5, 17));
    }

    @Test
    void updateProfile_emptyPhoneClearsIt() {
        UserAccount account = newAccount();
        account.updateProfile(null, null, "+221770000000", null, null);

        account.updateProfile(null, null, "", null, null);

        assertThat(account.getPhone()).isNull();
    }

    @Test
    void refreshTokenAndResetToken_stateMachines() {
        UserAccount account = newAccount();
        RefreshToken refresh = new RefreshToken(account, "h", NOW, NOW.plusSeconds(60));
        PasswordResetToken reset = new PasswordResetToken(account, "h", NOW, NOW.plusSeconds(60));

        assertThat(refresh.isRevoked()).isFalse();
        assertThat(refresh.isExpiredAt(NOW.plusSeconds(59))).isFalse();
        assertThat(refresh.isExpiredAt(NOW.plusSeconds(60))).isTrue();
        refresh.revoke(NOW.plusSeconds(1));
        refresh.revoke(NOW.plusSeconds(30)); // idempotent : la 1re date est conservée
        assertThat(refresh.isRevoked()).isTrue();
        assertThat(refresh.getRevokedAt()).isEqualTo(NOW.plusSeconds(1));

        assertThat(reset.isUsed()).isFalse();
        assertThat(reset.isExpiredAt(NOW.plusSeconds(60))).isTrue();
        reset.markUsed(NOW);
        assertThat(reset.isUsed()).isTrue();
    }
}
