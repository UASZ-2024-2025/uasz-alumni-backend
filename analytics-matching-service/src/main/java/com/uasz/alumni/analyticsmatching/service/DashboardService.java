package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.analyticsmatching.service.DashboardStatsDataSource.PlatformCounts;
import com.uasz.alumni.analyticsmatching.spi.model.DashboardStats;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ALUM-182 : calcul et mise en cache courte durée des statistiques globales du tableau de bord administrateur.
 */
@Service
public class DashboardService {

    private final DashboardStatsDataSource dataSource;
    private final Duration cacheTtl;
    private final Clock clock;
    private final AtomicReference<CachedStats> cache = new AtomicReference<>();

    @Autowired
    public DashboardService(DashboardStatsDataSource dataSource,
                            @Value("${analytics.dashboard.cache-ttl:PT5M}") Duration cacheTtl) {
        this(dataSource, cacheTtl, Clock.systemUTC());
    }

    DashboardService(DashboardStatsDataSource dataSource, Duration cacheTtl, Clock clock) {
        this.dataSource = dataSource;
        this.cacheTtl = cacheTtl == null ? Duration.ZERO : cacheTtl;
        this.clock = clock;
    }

    public DashboardStats getStats() {
        Instant now = clock.instant();
        CachedStats current = cache.get();
        if (current != null && !cacheTtl.isZero() && !cacheTtl.isNegative()
                && current.expiresAt().isAfter(now)) {
            return current.stats();
        }
        synchronized (this) {
            now = clock.instant();
            current = cache.get();
            if (current != null && !cacheTtl.isZero() && !cacheTtl.isNegative()
                    && current.expiresAt().isAfter(now)) {
                return current.stats();
            }
            DashboardStats computed = compute(now);
            if (!cacheTtl.isZero() && !cacheTtl.isNegative()) {
                cache.set(new CachedStats(computed, now.plus(cacheTtl)));
            }
            return computed;
        }
    }

    private DashboardStats compute(Instant now) {
        PlatformCounts counts = dataSource.fetchCounts();
        float insertionRate = counts.alumniCount() > 0
                ? Math.round((counts.employedAlumniCount() * 1000.0f) / counts.alumniCount()) / 10.0f
                : 0.0f;

        return new DashboardStats()
                .alumniCount(counts.alumniCount())
                .studentCount(counts.studentCount())
                .companyCount(counts.companyCount())
                .insertionRate(insertionRate)
                .internshipCount(counts.internshipCount())
                .jobOfferCount(counts.jobOfferCount())
                .updatedAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    private record CachedStats(DashboardStats stats, Instant expiresAt) {
    }
}
