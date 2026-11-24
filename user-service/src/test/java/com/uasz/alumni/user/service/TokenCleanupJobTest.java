
package com.uasz.alumni.user.service;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.clock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.repository.PasswordResetTokenRepository;
import com.uasz.alumni.user.repository.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenCleanupJobTest {

    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordResetTokenRepository resetTokens;

    @Test
    void purgeExpired_deletesTokensExpiredForMoreThanOneDay() {
        when(refreshTokens.deleteExpired(any(Instant.class))).thenReturn(3);
        when(resetTokens.deleteExpired(any(Instant.class))).thenReturn(1);

        new TokenCleanupJob(refreshTokens, resetTokens, clock()).purgeExpired();

        Instant limit = NOW.minus(Duration.ofDays(1));
        verify(refreshTokens).deleteExpired(limit);
        verify(resetTokens).deleteExpired(limit);
    }
}
