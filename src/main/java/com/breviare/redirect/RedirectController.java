package com.breviare.redirect;

import com.breviare.analytics.AnalyticsService;
import com.breviare.links.Link;
import com.breviare.links.LinkService;
import com.breviare.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
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

@RestController
public class RedirectController {

    private final LinkService linkService;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    public RedirectController(LinkService linkService, UserRepository userRepository, AnalyticsService analyticsService) {
        this.linkService = linkService;
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Void> resolve(@PathVariable String slug, HttpServletRequest request) {
        // Strip display dash: aBc-DeF → aBcDeF
        String normalized = slug.replace("-", "");

        // Short code: 6 chars after stripping dash
        if (normalized.length() == 6) {
            Optional<Link> found = linkService.findByCodeForRedirect(normalized);
            if (found.isPresent()) {
                Link link = found.get();

                if (link.isExpired() || isExpiredNow(link)) {
                    return ResponseEntity.status(HttpStatus.GONE).build();
                }

                analyticsService.recordClick(link, request);
                linkService.recordClick(link);

                return redirect(link.getDestination());
            }
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping("/u/{username}")
    public ResponseEntity<Void> resolveVanity(@PathVariable String username) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    if (user.getVanityDestination() == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).<Void>build();
                    }
                    return redirect(user.getVanityDestination());
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"ok\"}");
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
