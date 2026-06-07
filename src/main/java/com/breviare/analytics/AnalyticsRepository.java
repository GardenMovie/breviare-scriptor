package com.breviare.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalyticsRepository extends JpaRepository<AnalyticsEvent, Long> {

    @Query("SELECT a.countryCode, COUNT(a) FROM AnalyticsEvent a WHERE a.link.id = :linkId GROUP BY a.countryCode ORDER BY COUNT(a) DESC")
    List<Object[]> countByCountryForLink(UUID linkId);

    @Query("SELECT a.referrer, COUNT(a) FROM AnalyticsEvent a WHERE a.link.id = :linkId AND a.referrer IS NOT NULL GROUP BY a.referrer ORDER BY COUNT(a) DESC")
    List<Object[]> countByReferrerForLink(UUID linkId);

    @Query("SELECT DATE(a.clickedAt), COUNT(a) FROM AnalyticsEvent a WHERE a.link.id = :linkId AND a.clickedAt >= :since GROUP BY DATE(a.clickedAt) ORDER BY DATE(a.clickedAt)")
    List<Object[]> clicksPerDayForLink(UUID linkId, Instant since);
}
