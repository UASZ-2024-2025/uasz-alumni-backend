
package com.uasz.alumni.user.service;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.account;
import static com.uasz.alumni.user.UserTestData.authProps;
import static com.uasz.alumni.user.UserTestData.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.model.RefreshToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.RefreshTokenRepository;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock RefreshTokenRepository repo;

    private RefreshTokenService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repo, authProps(), clock());
        user = account(USER_ID, "awa.diop@uasz.sn", RoleName.ALUMNI, UserStatus.ACTIF);
    }

    @Test
    void issue_storesOnlyTheHash_andSetsExpiry() {
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(saved.capture());
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenUtils.sha256Hex(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(saved.getValue().isRevoked()).isFalse();
    }

    @Test
    void find_hashesTheTrimmedRawToken() {
        RefreshToken token = new RefreshToken(user, TokenUtils.sha256Hex("raw"), NOW, NOW.plusSeconds(60));
        when(repo.findByTokenHashWithUser(TokenUtils.sha256Hex("raw"))).thenReturn(Optional.of(token));

        assertThat(service.find("  raw ")).containsSame(token);
    }

    @Test
    void find_returnsEmptyForUnknownToken() {
        when(repo.findByTokenHashWithUser(TokenUtils.sha256Hex("inconnu"))).thenReturn(Optional.empty());

        assertThat(service.find("inconnu")).isEmpty();
    }

    @Test
    void revoke_revokesTokenOfTheSameUser() {
        RefreshToken token = new RefreshToken(user, TokenUtils.sha256Hex("raw"), NOW, NOW.plusSeconds(60));
        when(repo.findByTokenHashWithUser(TokenUtils.sha256Hex("raw"))).thenReturn(Optional.of(token));

        service.revoke(USER_ID, "raw");

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revoke_ignoresTokenOfAnotherUser() {
        RefreshToken token = new RefreshToken(user, TokenUtils.sha256Hex("raw"), NOW, NOW.plusSeconds(60));
        when(repo.findByTokenHashWithUser(TokenUtils.sha256Hex("raw"))).thenReturn(Optional.of(token));

        service.revoke(UUID.randomUUID(), "raw");

        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void revoke_ignoresUnknownToken() {
        when(repo.findByTokenHashWithUser(TokenUtils.sha256Hex("inconnu"))).thenReturn(Optional.empty());

        service.revoke(USER_ID, "inconnu"); // ne doit pas lever d'exception
    }

    @Test
    void revokeAll_delegatesToRepositoryWithCurrentInstant() {
        service.revokeAll(USER_ID);

        verify(repo).revokeAllByUserId(USER_ID, NOW);
    }
}
