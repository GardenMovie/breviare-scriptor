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
        boolean isExpired,
        Instant expiredAt
) {
    public static LinkResponse from(Link link, String baseUrl) {
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
                link.isExpired(),
                link.getExpiredAt()
        );
    }
}
