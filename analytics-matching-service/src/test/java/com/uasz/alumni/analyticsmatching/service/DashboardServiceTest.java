package com.uasz.alumni.analyticsmatching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.analyticsmatching.service.DashboardStatsDataSource.PlatformCounts;
import com.uasz.alumni.analyticsmatching.spi.model.DashboardStats;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration d) {
            this.current = this.current.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    @Test
    void calculeStatistiquesEtTauxInsertion() {
        DashboardStatsDataSource source = mock(DashboardStatsDataSource.class);
        when(source.fetchCounts()).thenReturn(new PlatformCounts(200, 177, 150, 40, 35, 65));
        MutableClock clock = new MutableClock(Instant.parse("2026-11-24T12:00:00Z"));
        DashboardService service = new DashboardService(source, Duration.ofMinutes(5), clock);

        DashboardStats stats = service.getStats();

        assertEquals(200, stats.getAlumniCount());
        assertEquals(150, stats.getStudentCount());
        assertEquals(40, stats.getCompanyCount());
        assertEquals(88.5f, stats.getInsertionRate());
        assertEquals(35, stats.getInternshipCount());
        assertEquals(65, stats.getJobOfferCount());
        assertNotNull(stats.getUpdatedAt());
    }

    @Test
    void aucunAlumniDonneTauxInsertionZero() {
        DashboardStatsDataSource source = mock(DashboardStatsDataSource.class);
        when(source.fetchCounts()).thenReturn(new PlatformCounts(0, 0, 10, 5, 2, 3));
        DashboardService service = new DashboardService(source, Duration.ZERO, Clock.systemUTC());

        DashboardStats stats = service.getStats();

        assertEquals(0.0f, stats.getInsertionRate());
    }

    @Test
    void utiliseLeCachePendantLeTtlEtRecalculeApresExpiration() {
        DashboardStatsDataSource source = mock(DashboardStatsDataSource.class);
        when(source.fetchCounts())
                .thenReturn(new PlatformCounts(100, 80, 50, 10, 5, 15))
                .thenReturn(new PlatformCounts(105, 84, 52, 11, 6, 16));
        MutableClock clock = new MutableClock(Instant.parse("2026-11-24T12:00:00Z"));
        DashboardService service = new DashboardService(source, Duration.ofMinutes(5), clock);

        DashboardStats first = service.getStats();
        clock.advance(Duration.ofMinutes(2));
        DashboardStats cached = service.getStats();
        assertEquals(first.getAlumniCount(), cached.getAlumniCount());
        verify(source, times(1)).fetchCounts();

        clock.advance(Duration.ofMinutes(4));
        DashboardStats refreshed = service.getStats();
        assertEquals(105, refreshed.getAlumniCount());
        verify(source, times(2)).fetchCounts();
    }
}
