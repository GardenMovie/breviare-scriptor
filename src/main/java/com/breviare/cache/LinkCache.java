package com.breviare.cache;

import com.breviare.links.Link;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Redis read cache for the short-link redirect path (GET /{slug}).
 *
 * Only exists when breviare.cache.enabled=true. Design rules:
 *  - Fail open: any Redis error is treated as a miss, so Redis can never take redirects down.
 *  - Only a cached link that is still live is trusted. Anything that looks expired falls through
 *    to Postgres, which stays the source of truth for 410s and for inactivity expiry.
 *  - Writers (update/delete/create) evict the key; entries also expire after ttl-seconds.
 *  - Unknown slugs are cached briefly as a negative entry to absorb slug scanning.
 */
@Component
@ConditionalOnProperty(name = "breviare.cache.enabled", havingValue = "true")
public class LinkCache {

    private static final Logger log = LoggerFactory.getLogger(LinkCache.class);
    private static final String KEY_PREFIX = "link:";
    private static final String NEGATIVE_MARKER = "-";

    /** What the redirect needs to decide without touching Postgres. */
    public record CachedLink(UUID id, String destination, boolean expired, Instant lastClickedAt,
                             int inactivityTtlDays, Instant absoluteExpiresAt) {

        static CachedLink from(Link link) {
            return new CachedLink(link.getId(), link.getDestination(), link.isExpired(),
                    link.getLastClickedAt(), link.getInactivityTtlDays(), link.getAbsoluteExpiresAt());
        }

        /** True only if the link is certainly still redirectable. False means "ask Postgres". */
        public boolean isLive(Instant now) {
            if (expired) return false;
            if (absoluteExpiresAt != null && absoluteExpiresAt.isBefore(now)) return false;
            return !lastClickedAt.plusSeconds((long) inactivityTtlDays * 86400).isBefore(now);
        }
    }

    public enum State { HIT, KNOWN_ABSENT, MISS }

    public record Lookup(State state, CachedLink link) {
        static final Lookup MISS = new Lookup(State.MISS, null);
        static final Lookup KNOWN_ABSENT = new Lookup(State.KNOWN_ABSENT, null);
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;
    private final Duration negativeTtl;

    public LinkCache(StringRedisTemplate redis, ObjectMapper mapper,
                     @Value("${breviare.cache.ttl-seconds}") long ttlSeconds,
                     @Value("${breviare.cache.negative-ttl-seconds}") long negativeTtlSeconds) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.negativeTtl = Duration.ofSeconds(negativeTtlSeconds);
    }

    public Lookup lookup(String code) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + code);
            if (value == null) return Lookup.MISS;
            if (NEGATIVE_MARKER.equals(value)) return Lookup.KNOWN_ABSENT;
            return new Lookup(State.HIT, mapper.readValue(value, CachedLink.class));
        } catch (Exception e) {
            failOpen("lookup", e);
            return Lookup.MISS;
        }
    }

    public void put(String code, Link link) {
        try {
            redis.opsForValue().set(KEY_PREFIX + code, mapper.writeValueAsString(CachedLink.from(link)), ttl);
        } catch (Exception e) {
            failOpen("put", e);
        }
    }

    public void putAbsent(String code) {
        try {
            redis.opsForValue().set(KEY_PREFIX + code, NEGATIVE_MARKER, negativeTtl);
        } catch (Exception e) {
            failOpen("putAbsent", e);
        }
    }

    public void evict(String code) {
        try {
            redis.delete(KEY_PREFIX + code);
        } catch (Exception e) {
            // Louder than the other ops: a failed evict leaves a stale entry until the TTL runs out.
            log.warn("Redis cache evict failed for {}; entry may be stale for up to {}s: {}",
                    code, ttl.toSeconds(), e.toString());
        }
    }

    private void failOpen(String op, Exception e) {
        // Debug-level on purpose: if Redis is down this fires on every redirect.
        log.debug("Redis cache {} failed, falling back to Postgres: {}", op, e.toString());
    }
}
