package com.breviare.analytics;

import com.breviare.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/links/{code}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final com.breviare.links.LinkService linkService;

    public AnalyticsController(AnalyticsService analyticsService, com.breviare.links.LinkService linkService) {
        this.analyticsService = analyticsService;
        this.linkService = linkService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics(
            @PathVariable String code,
            @AuthenticationPrincipal UserDetails principal
    ) {
        UUID requesterId = UUID.fromString(principal.getUsername());
        var link = linkService.getByCode(code, requesterId);

        var dailyClicks = analyticsService.dailyClicksLast7Days(link.getId());

        return ResponseEntity.ok(ApiResponse.ok(Map.of("dailyClicks", dailyClicks)));
    }
}
