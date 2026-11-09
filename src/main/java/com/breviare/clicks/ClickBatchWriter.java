package com.breviare.clicks;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Writes a batch of click events to Postgres in one transaction, using one statement per table
 * (unnest over arrays) instead of one statement per click:
 *  - every event becomes its own row in analytics_events / vanity_events (nothing is merged),
 *  - the per-link / per-user counters are bumped once per batch by the number of clicks.
 *
 * Events whose link or user was deleted after the click are skipped (EXISTS guard) instead of failing
 * the whole batch on the foreign key.
 */
@Component
@ConditionalOnProperty(name = "breviare.clicks.queue.enabled", havingValue = "true")
public class ClickBatchWriter {

    private static final String INSERT_LINK_EVENTS = """
            INSERT INTO analytics_events (link_id, clicked_at, referrer, user_agent, ip_hash)
            SELECT t.id, t.ts, t.ref, t.ua, t.ip
            FROM unnest(?::uuid[], ?::timestamptz[], ?::text[], ?::text[], ?::text[]) AS t(id, ts, ref, ua, ip)
            WHERE EXISTS (SELECT 1 FROM links l WHERE l.id = t.id)
            """;

    private static final String INSERT_VANITY_EVENTS = """
            INSERT INTO vanity_events (user_id, clicked_at, referrer, user_agent, ip_hash)
            SELECT t.id, t.ts, t.ref, t.ua, t.ip
            FROM unnest(?::uuid[], ?::timestamptz[], ?::text[], ?::text[], ?::text[]) AS t(id, ts, ref, ua, ip)
            WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = t.id)
            """;

    private static final String BUMP_LINKS = """
            UPDATE links l
            SET click_count = l.click_count + t.n, last_clicked_at = GREATEST(l.last_clicked_at, t.ts)
            FROM unnest(?::uuid[], ?::bigint[], ?::timestamptz[]) AS t(id, n, ts)
            WHERE l.id = t.id
            """;

    private static final String BUMP_USERS = """
            UPDATE users u
            SET vanity_click_count = u.vanity_click_count + t.n
            FROM unnest(?::uuid[], ?::bigint[]) AS t(id, n)
            WHERE u.id = t.id
            """;

    private final JdbcTemplate jdbc;

    public ClickBatchWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void write(List<ClickEvent> events) {
        List<ClickEvent> links = new ArrayList<>();
        List<ClickEvent> vanity = new ArrayList<>();
        for (ClickEvent e : events) (e.type() == ClickEvent.Type.LINK ? links : vanity).add(e);

        if (!links.isEmpty()) {
            insertEvents(INSERT_LINK_EVENTS, links);
            bumpLinks(links);
        }
        if (!vanity.isEmpty()) {
            insertEvents(INSERT_VANITY_EVENTS, vanity);
            bumpUsers(vanity);
        }
    }

    private void insertEvents(String sql, List<ClickEvent> batch) {
        jdbc.update(sql, ps -> {
            Connection con = ps.getConnection();
            ps.setArray(1, con.createArrayOf("uuid", batch.stream().map(ClickEvent::targetId).toArray(UUID[]::new)));
            ps.setArray(2, con.createArrayOf("timestamptz", batch.stream().map(e -> Timestamp.from(e.clickedAt())).toArray(Timestamp[]::new)));
            ps.setArray(3, con.createArrayOf("text", batch.stream().map(ClickEvent::referrer).toArray(String[]::new)));
            ps.setArray(4, con.createArrayOf("text", batch.stream().map(ClickEvent::userAgent).toArray(String[]::new)));
            ps.setArray(5, con.createArrayOf("text", batch.stream().map(ClickEvent::ipHash).toArray(String[]::new)));
        });
    }

    private void bumpLinks(List<ClickEvent> batch) {
        // Sorted by id so concurrent writers always lock rows in the same order.
        Map<UUID, long[]> counts = new TreeMap<>();      // [count]
        Map<UUID, Instant> latest = new TreeMap<>();
        for (ClickEvent e : batch) {
            counts.computeIfAbsent(e.targetId(), k -> new long[1])[0]++;
            latest.merge(e.targetId(), e.clickedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        jdbc.update(BUMP_LINKS, ps -> {
            Connection con = ps.getConnection();
            ps.setArray(1, con.createArrayOf("uuid", counts.keySet().toArray(UUID[]::new)));
            ps.setArray(2, con.createArrayOf("bigint", counts.values().stream().map(c -> c[0]).toArray(Long[]::new)));
            ps.setArray(3, con.createArrayOf("timestamptz", counts.keySet().stream().map(id -> Timestamp.from(latest.get(id))).toArray(Timestamp[]::new)));
        });
    }

    private void bumpUsers(List<ClickEvent> batch) {
        Map<UUID, long[]> counts = new TreeMap<>();
        for (ClickEvent e : batch) counts.computeIfAbsent(e.targetId(), k -> new long[1])[0]++;
        jdbc.update(BUMP_USERS, ps -> {
            Connection con = ps.getConnection();
            ps.setArray(1, con.createArrayOf("uuid", counts.keySet().toArray(UUID[]::new)));
            ps.setArray(2, con.createArrayOf("bigint", counts.values().stream().map(c -> c[0]).toArray(Long[]::new)));
        });
    }
}
