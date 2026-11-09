package com.breviare.redirect;

import com.breviare.analytics.AnalyticsService;
import com.breviare.cache.LinkCache;
import com.breviare.clicks.ClickEvent;
import com.breviare.clicks.ClickQueue;
import com.breviare.links.Link;
import com.breviare.links.LinkService;
import com.breviare.users.UserRepository;
import com.breviare.users.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
public class RedirectController {

    private final LinkService linkService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AnalyticsService analyticsService;
    // null unless breviare.cache.enabled=true; when null the redirect path is exactly the original one.
    private final LinkCache linkCache;
    // null unless breviare.clicks.queue.enabled=true; when null every click is written to Postgres directly.
    private final ClickQueue clickQueue;

    public RedirectController(LinkService linkService, UserRepository userRepository,
                              UserService userService, AnalyticsService analyticsService,
                              ObjectProvider<LinkCache> linkCache, ObjectProvider<ClickQueue> clickQueue) {
        this.linkService = linkService;
        this.userRepository = userRepository;
        this.userService = userService;
        this.analyticsService = analyticsService;
        this.linkCache = linkCache.getIfAvailable();
        this.clickQueue = clickQueue.getIfAvailable();
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Void> resolve(@PathVariable String slug, HttpServletRequest request) {
        // Strip display dash: aBc-DeF → aBcDeF
        String normalized = slug.replace("-", "");

        // Short code: 6 chars after stripping dash
        if (normalized.length() == 6) {
            if (linkCache != null) {
                ResponseEntity<Void> cached = resolveFromCache(normalized, request);
                if (cached != null) return cached;
            }

            Optional<Link> found = linkService.findByCodeForRedirect(normalized);
            if (found.isPresent()) {
                Link link = found.get();

                if (link.isExpired() || isExpiredNow(link)) {
                    return ResponseEntity.status(HttpStatus.GONE).build();
                }

                if (linkCache != null) linkCache.put(normalized, link);

                if (!enqueueClick(ClickEvent.Type.LINK, link.getId(), request)) {
                    analyticsService.recordClick(link, request);
                    linkService.recordClick(link);
                }

                return redirect(link.getDestination());
            }

            if (linkCache != null) linkCache.putAbsent(normalized);
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // Returns a response only when the cache can answer with certainty (a live link, or a known-unknown
    // slug). Returns null for a miss, an error, or anything that looks expired, so Postgres decides.
    private ResponseEntity<Void> resolveFromCache(String code, HttpServletRequest request) {
        LinkCache.Lookup lookup = linkCache.lookup(code);
        switch (lookup.state()) {
            case KNOWN_ABSENT:
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            case HIT:
                LinkCache.CachedLink cached = lookup.link();
                if (cached.isLive(Instant.now())) {
                    if (!enqueueClick(ClickEvent.Type.LINK, cached.id(), request)) {
                        analyticsService.recordClick(cached.id(), request);
                        linkService.recordClick(cached.id());
                    }
                    return redirect(cached.destination());
                }
                return null;
            default:
                return null;
        }
    }

    // Vanity links never expire, so there is no expiry check here to mirror the short-code path.
    @GetMapping("/u/{username}")
    public ResponseEntity<Void> resolveVanity(@PathVariable String username, HttpServletRequest request) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    if (user.getVanityDestination() == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).<Void>build();
                    }

                    if (!enqueueClick(ClickEvent.Type.VANITY, user.getId(), request)) {
                        analyticsService.recordVanityClick(user, request);
                        userService.recordVanityClick(user.getId());
                    }

                    return redirect(user.getVanityDestination());
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"ok\"}");
    }

    // True if the click was queued in Redis; false means the caller must write it to Postgres itself
    // (queue disabled, or Redis unavailable).
    private boolean enqueueClick(ClickEvent.Type type, UUID targetId, HttpServletRequest request) {
        return clickQueue != null && clickQueue.offer(analyticsService.clickEvent(type, targetId, request));
    }

    private boolean isExpiredNow(Link link) {
        Instant now = Instant.now();
        boolean inactivity = link.getLastClickedAt()
                .plusSeconds((long) link.getInactivityTtlDays() * 86400)
                .isBefore(now);
        boolean absolute = link.getAbsoluteExpiresAt() != null && link.getAbsoluteExpiresAt().isBefore(now);
        return inactivity || absolute;
    }

    private ResponseEntity<Void> redirect(String destination) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(destination));
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
