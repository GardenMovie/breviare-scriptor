package com.breviare.analytics;

import com.breviare.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/links/{code}/analytics")
public class AnalyticsController {

    private final AnalyticsRepository analyticsRepository;
    private final com.breviare.links.LinkService linkService;

    public AnalyticsController(AnalyticsRepository analyticsRepository, com.breviare.links.LinkService linkService) {
        this.analyticsRepository = analyticsRepository;
        this.linkService = linkService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics(
            @PathVariable String code,
            @AuthenticationPrincipal UserDetails principal
    ) {
        UUID requesterId = UUID.fromString(principal.getUsername());
        var link = linkService.getByCode(code, requesterId);

        List<Object[]> countries = analyticsRepository.countByCountryForLink(link.getId());
        List<Object[]> referrers = analyticsRepository.countByReferrerForLink(link.getId());

        Map<String, Object> result = Map.of(
                "countries", toMap(countries),
                "referrers", toMap(referrers),
                "totalClicks", link.getClickCount()
        );

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                r -> r[0] != null ? r[0].toString() : "unknown",
                r -> (Long) r[1]
        ));
    }
}
