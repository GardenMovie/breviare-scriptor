package com.breviare.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AnalyticsRepository extends JpaRepository<AnalyticsEvent, Long> {

    @Query("SELECT a.countryCode, COUNT(a) FROM AnalyticsEvent a WHERE a.link.id = :linkId GROUP BY a.countryCode ORDER BY COUNT(a) DESC")
    List<Object[]> countByCountryForLink(UUID linkId);

    @Query("SELECT a.referrer, COUNT(a) FROM AnalyticsEvent a WHERE a.link.id = :linkId AND a.referrer IS NOT NULL GROUP BY a.referrer ORDER BY COUNT(a) DESC")
    List<Object[]> countByReferrerForLink(UUID linkId);

    @Query("SELECT DATE(a.clickedAt), COUNT(a) FROM AnalyticsEvent a WHERE a.link.id = :linkId AND a.clickedAt >= :since GROUP BY DATE(a.clickedAt) ORDER BY DATE(a.clickedAt)")
    List<Object[]> clicksPerDayForLink(UUID linkId, Instant since);

    // Recomputes daily rollups for [since, now) from raw events; overwrites existing rows for that
    // window so late-arriving events or a rerun after a failed job self-correct. Never touches days
    // before `since`, so the job stays cheap regardless of how large analytics_events grows.
    @Modifying
    @Query(value = """
            INSERT INTO link_daily_clicks (link_id, day, click_count)
            SELECT link_id, DATE(clicked_at AT TIME ZONE 'UTC'), COUNT(*)
            FROM analytics_events
            WHERE clicked_at >= :since
            GROUP BY link_id, DATE(clicked_at AT TIME ZONE 'UTC')
            ON CONFLICT (link_id, day) DO UPDATE SET click_count = EXCLUDED.click_count
            """, nativeQuery = true)
    void rollupDailyClicksSince(@Param("since") Instant since);

    @Query(value = """
            SELECT COALESCE(SUM(click_count), 0)
            FROM link_daily_clicks
            WHERE link_id = :linkId AND day >= :sinceDay
            """, nativeQuery = true)
    long sumDailyClicksSince(@Param("linkId") UUID linkId, @Param("sinceDay") LocalDate sinceDay);

    @Query(value = """
            SELECT day, click_count
            FROM link_daily_clicks
            WHERE link_id = :linkId AND day >= :sinceDay
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> dailyClicksSince(@Param("linkId") UUID linkId, @Param("sinceDay") LocalDate sinceDay);

    // Batched per-link sums for a page of links, avoiding N+1 queries.
    @Query(value = """
            SELECT link_id, COALESCE(SUM(click_count), 0)
            FROM link_daily_clicks
            WHERE link_id IN (:linkIds) AND day >= :sinceDay
            GROUP BY link_id
            """, nativeQuery = true)
    List<Object[]> sumDailyClicksSinceForLinks(@Param("linkIds") List<UUID> linkIds, @Param("sinceDay") LocalDate sinceDay);

    @Query("SELECT COUNT(a) FROM AnalyticsEvent a WHERE a.link.id = :linkId AND a.clickedAt >= :since")
    long countSince(@Param("linkId") UUID linkId, @Param("since") Instant since);

    // Batched "today so far" counts (live, not rolled up yet) for a page of links.
    @Query("SELECT a.link.id, COUNT(a) FROM AnalyticsEvent a WHERE a.link.id IN :linkIds AND a.clickedAt >= :since GROUP BY a.link.id")
    List<Object[]> countSinceForLinks(@Param("linkIds") List<UUID> linkIds, @Param("since") Instant since);
}
