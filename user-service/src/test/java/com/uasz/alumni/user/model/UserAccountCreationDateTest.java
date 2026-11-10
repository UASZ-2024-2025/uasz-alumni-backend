
package com.uasz.alumni.user.model;

import static com.uasz.alumni.user.UserTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import org.junit.jupiter.api.Test;

/** Constructeur avec horodatage fourni (horloge injectée). */
class UserAccountCreationDateTest {

    @Test
    void constructorWithTimestamp_setsCreatedAndUpdatedAt() {
        UserAccount account = new UserAccount("a@uasz.sn", "HASH", "Awa", "Diop",
                RoleName.ALUMNI, UserStatus.EN_ATTENTE, NOW);

        assertThat(account.getCreatedAt()).isEqualTo(NOW);
        assertThat(account.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void constructorWithoutTimestamp_leavesDatesForJpa() {
        UserAccount account = new UserAccount("a@uasz.sn", "HASH", "Awa", "Diop",
                RoleName.ALUMNI, UserStatus.EN_ATTENTE);

        assertThat(account.getCreatedAt()).isNull();
    }
}
