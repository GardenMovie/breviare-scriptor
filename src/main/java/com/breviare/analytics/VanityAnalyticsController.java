package com.breviare.analytics;

import com.breviare.common.ApiResponse;
import com.breviare.users.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

// Addressed as /me rather than by username or code: a user's vanity analytics are reachable only
// by the authenticated owner, and there is no identifier in the path to guess.
@RestController
@RequestMapping("/api/v1/me/vanity/analytics")
public class VanityAnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService userService;

    public VanityAnalyticsController(AnalyticsService analyticsService, UserService userService) {
        this.analyticsService = analyticsService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVanityAnalytics(
            @AuthenticationPrincipal UserDetails principal
    ) {
        UUID requesterId = UUID.fromString(principal.getUsername());
        var user = userService.getById(requesterId);

        // Same {day, clicks} shape as the link analytics endpoint so the existing chart renders both.
        var dailyClicks = analyticsService.vanityDailyClicksLast7Days(requesterId);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "dailyClicks", dailyClicks,
                "clicksLast7Days", analyticsService.vanityClicksLast7Days(requesterId),
                "totalClicks", user.getVanityClickCount()
        )));
    }
}
