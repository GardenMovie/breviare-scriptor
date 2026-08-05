package com.breviare.analytics;

import com.breviare.links.Link;
import com.breviare.users.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class AnalyticsService {

    // How far back the nightly rollup recomputes on each run. Wider than 1 day to absorb late
    // @Async writes landing after midnight and to make reruns after a failed job self-correcting.
    // Days older than this window are never revisited, so the job stays cheap as analytics_events grows.
    private static final int ROLLUP_WINDOW_DAYS = 3;
    private static final int STATS_WINDOW_DAYS = 7;

    private final AnalyticsRepository analyticsRepository;
    private final VanityAnalyticsRepository vanityAnalyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository,
                            VanityAnalyticsRepository vanityAnalyticsRepository) {
        this.analyticsRepository = analyticsRepository;
        this.vanityAnalyticsRepository = vanityAnalyticsRepository;
    }

    @Async
    @Transactional
    public void recordClick(Link link, HttpServletRequest request) {
        String ip = resolveClientIp(request);
        String ipHash = ip != null ? sha256(ip) : null;
        String referrer = request.getHeader("Referer");
        String userAgent = request.getHeader("User-Agent");

        // TODO: resolve countryCode from ip via GeoIP lookup instead of passing null
        analyticsRepository.save(new AnalyticsEvent(link, referrer, userAgent, ipHash, null));
    }

    @Async
    @Transactional
    public void recordVanityClick(User user, HttpServletRequest request) {
        String ip = resolveClientIp(request);
        String ipHash = ip != null ? sha256(ip) : null;
        String referrer = request.getHeader("Referer");
        String userAgent = request.getHeader("User-Agent");

        // TODO: resolve countryCode from ip via GeoIP lookup instead of passing null
        vanityAnalyticsRepository.save(new VanityEvent(user, referrer, userAgent, ipHash, null));
    }

    // One job for both pipelines: same window, same transaction, so link and vanity rollups can
    // never disagree about which days have been recomputed.
    @Scheduled(cron = "0 30 2 * * *") // nightly, before the 3am blocklist sync
    @Transactional
    public void rollupDailyClicks() {
        Instant since = LocalDate.now(ZoneOffset.UTC).minusDays(ROLLUP_WINDOW_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant();
        analyticsRepository.rollupDailyClicksSince(since);
        vanityAnalyticsRepository.rollupDailyClicksSince(since);
    }

    // Last 7 days of clicks for a single link: days 1-6 from the rollup table, plus a live
    // count for today (which the nightly job hasn't rolled up yet).
    public long clicksLast7Days(UUID linkId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long rolledUp = analyticsRepository.sumDailyClicksSince(linkId, today.minusDays(STATS_WINDOW_DAYS - 1L));
        long liveToday = analyticsRepository.countSince(linkId, today.atStartOfDay(ZoneOffset.UTC).toInstant());
        return rolledUp + liveToday;
    }

    // Per-day breakdown for the last 7 days: days 1-6 from the rollup table, today live, in
    // chronological order. Zero-fills days with no rows/clicks so the caller always gets exactly
    // 7 entries.
    public List<Map<String, Object>> dailyClicksLast7Days(UUID linkId) {
        return dailyClicksLast7Days(
                () -> analyticsRepository.dailyClicksSince(linkId, statsWindowStart()),
                since -> analyticsRepository.countSince(linkId, since));
    }

    // Vanity equivalent, returning the identical {day, clicks} shape so the same chart renders both.
    public List<Map<String, Object>> vanityDailyClicksLast7Days(UUID userId) {
        return dailyClicksLast7Days(
                () -> vanityAnalyticsRepository.dailyClicksSince(userId, statsWindowStart()),
                since -> vanityAnalyticsRepository.countSince(userId, since));
    }

    public long vanityClicksLast7Days(UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long rolledUp = vanityAnalyticsRepository.sumDailyClicksSince(userId, statsWindowStart());
        long liveToday = vanityAnalyticsRepository.countSince(userId, today.atStartOfDay(ZoneOffset.UTC).toInstant());
        return rolledUp + liveToday;
    }

    // Shared shape for both pipelines: rolled-up rows for days 1-6, a live count for today (which
    // the nightly job hasn't rolled up yet), zero-filled to exactly STATS_WINDOW_DAYS entries.
    private List<Map<String, Object>> dailyClicksLast7Days(Supplier<List<Object[]>> rolledUpRows,
                                                           LongFunction liveTodayCount) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate sinceDay = statsWindowStart();

        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : rolledUpRows.get()) {
            LocalDate day = row[0] instanceof LocalDate ld ? ld : ((java.sql.Date) row[0]).toLocalDate();
            byDay.put(day, ((Number) row[1]).longValue());
        }
        byDay.put(today, liveTodayCount.apply(today.atStartOfDay(ZoneOffset.UTC).toInstant()));

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (LocalDate day = sinceDay; !day.isAfter(today); day = day.plusDays(1)) {
            result.add(Map.of("day", day.toString(), "clicks", byDay.getOrDefault(day, 0L)));
        }
        return result;
    }

    private LocalDate statsWindowStart() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(STATS_WINDOW_DAYS - 1L);
    }

    @FunctionalInterface
    private interface LongFunction {
        long apply(Instant since);
    }

    // Batched equivalent of clicksLast7Days for a page of links, to avoid N+1 queries from listForOwner.
    public Map<UUID, Long> clicksLast7DaysForLinks(List<UUID> linkIds) {
        if (linkIds.isEmpty()) return Map.of();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<UUID, Long> result = new HashMap<>();
        for (UUID id : linkIds) result.put(id, 0L);
        for (Object[] row : analyticsRepository.sumDailyClicksSinceForLinks(linkIds, today.minusDays(STATS_WINDOW_DAYS - 1L))) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        for (Object[] row : analyticsRepository.countSinceForLinks(linkIds, today.atStartOfDay(ZoneOffset.UTC).toInstant())) {
            UUID id = (UUID) row[0];
            result.merge(id, ((Number) row[1]).longValue(), Long::sum);
        }
        return result;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
