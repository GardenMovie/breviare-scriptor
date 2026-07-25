package com.breviare.links;

import java.time.Instant;

public record LinkResponse(
        String code,
        String displayCode,
        String shortUrl,
        String destination,
        String ownerId,
        Instant createdAt,
        Instant lastClickedAt,
        int inactivityTtlDays,
        Instant absoluteExpiresAt,
        long clickCount,
        long clicksLast7Days,
        boolean isExpired,
        Instant expiredAt
) {
    // Convenience for call sites with no meaningful "last 7 days" figure yet, e.g. a just-created link.
    public static LinkResponse from(Link link, String baseUrl) {
        return from(link, baseUrl, 0L);
    }

    public static LinkResponse from(Link link, String baseUrl, long clicksLast7Days) {
        String code = link.getCode();
        String display = code.substring(0, 3) + "-" + code.substring(3);
        return new LinkResponse(
                code,
                display,
                baseUrl + "/" + display,
                link.getDestination(),
                link.getOwner() != null ? link.getOwner().getId().toString() : null,
                link.getCreatedAt(),
                link.getLastClickedAt(),
                link.getInactivityTtlDays(),
                link.getAbsoluteExpiresAt(),
                link.getClickCount(),
                clicksLast7Days,
                link.isExpired(),
                link.getExpiredAt()
        );
    }
}
