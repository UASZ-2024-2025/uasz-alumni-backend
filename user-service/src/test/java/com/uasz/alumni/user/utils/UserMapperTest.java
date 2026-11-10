
package com.uasz.alumni.user.utils;

import static com.uasz.alumni.user.UserTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.spi.model.Gender;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserMapperTest {

    @Test
    void toApi_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UserAccount account = new UserAccount("awa.diop@uasz.sn", "HASH", "Awa", "Diop",
                RoleName.ENTREPRISE, UserStatus.ACTIF);
        account.updateProfile(null, null, "+221770000000", Gender.FEMALE, LocalDate.of(1998, 5, 17));
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "createdAt", NOW);

        User api = UserMapper.toApi(account);

        assertThat(api.getId()).isEqualTo(id);
        assertThat(api.getEmail()).isEqualTo("awa.diop@uasz.sn");
        assertThat(api.getFirstName()).isEqualTo("Awa");
        assertThat(api.getLastName()).isEqualTo("Diop");
        assertThat(api.getRole()).isEqualTo(RoleName.ENTREPRISE);
        assertThat(api.getStatus()).isEqualTo(UserStatus.ACTIF);
        assertThat(api.getPhone()).isEqualTo("+221770000000");
        assertThat(api.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(api.getBirthDate()).isEqualTo(LocalDate.of(1998, 5, 17));
        assertThat(api.getCreatedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
    }

    @Test
    void toApi_toleratesNotYetPersistedAccount() {
        UserAccount account = new UserAccount("a@uasz.sn", "HASH", "A", "B", RoleName.ETUDIANT, UserStatus.EN_ATTENTE);

        User api = UserMapper.toApi(account);

        assertThat(api.getCreatedAt()).isNull();
        assertThat(api.getPhone()).isNull();
        assertThat(api.getGender()).isNull();
    }
}
