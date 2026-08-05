package com.breviare.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VanityAnalyticsRepository extends JpaRepository<VanityEvent, Long> {

    // Mirrors AnalyticsRepository.rollupDailyClicksSince, over vanity_events/vanity_daily_clicks.
    // Recomputes [since, now) and overwrites existing rows so late @Async writes and reruns after
    // a failed job self-correct.
    @Modifying
    @Query(value = """
            INSERT INTO vanity_daily_clicks (user_id, day, click_count)
            SELECT user_id, DATE(clicked_at AT TIME ZONE 'UTC'), COUNT(*)
            FROM vanity_events
            WHERE clicked_at >= :since
            GROUP BY user_id, DATE(clicked_at AT TIME ZONE 'UTC')
            ON CONFLICT (user_id, day) DO UPDATE SET click_count = EXCLUDED.click_count
            """, nativeQuery = true)
    void rollupDailyClicksSince(@Param("since") Instant since);

    @Query(value = """
            SELECT day, click_count
            FROM vanity_daily_clicks
            WHERE user_id = :userId AND day >= :sinceDay
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> dailyClicksSince(@Param("userId") UUID userId, @Param("sinceDay") LocalDate sinceDay);

    @Query(value = """
            SELECT COALESCE(SUM(click_count), 0)
            FROM vanity_daily_clicks
            WHERE user_id = :userId AND day >= :sinceDay
            """, nativeQuery = true)
    long sumDailyClicksSince(@Param("userId") UUID userId, @Param("sinceDay") LocalDate sinceDay);

    @Query("SELECT COUNT(v) FROM VanityEvent v WHERE v.user.id = :userId AND v.clickedAt >= :since")
    long countSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
