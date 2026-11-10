package com.uasz.alumni.user.service;

import com.uasz.alumni.user.repository.PasswordResetTokenRepository;
import com.uasz.alumni.user.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Purge quotidienne des jetons expirés depuis plus d'un jour (suppression idempotente : sans risque avec 2 réplicas). */
@Component
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final Clock clock;

    public TokenCleanupJob(RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        Instant limit = clock.instant().minus(Duration.ofDays(1));
        int refresh = refreshTokens.deleteExpired(limit);
        int reset = resetTokens.deleteExpired(limit);
        log.info("Purge des jetons : {} refresh, {} reset", refresh, reset);
    }
}
