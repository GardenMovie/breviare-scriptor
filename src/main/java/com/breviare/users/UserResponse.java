package com.breviare.users;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

public record UserResponse(
        String id,
        String email,
        String username,
        String vanityDestination,
        Instant usernameChangedAt,
        int usernameChangesRemainingThisMonth,
        Instant vanityDestinationChangedAt,
        int vanityDestinationChangesRemainingThisMonth,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getUsername(),
                user.getVanityDestination(),
                user.getUsernameChangedAt(),
                changedThisMonth(user.getUsernameChangedAt()) ? 0 : 1,
                user.getVanityDestinationChangedAt(),
                Math.max(0, 5 - user.getVanityDestinationChangeCountThisMonth()),
                user.getCreatedAt()
        );
    }

    // Shouldnt it be 30 days? Montly works but its convoluted
    private static boolean changedThisMonth(Instant changedAt) {
        if (changedAt == null) {
            return false;
        }
        YearMonth changedMonth = YearMonth.from(changedAt.atOffset(ZoneOffset.UTC));
        return changedMonth.equals(YearMonth.now(ZoneOffset.UTC));
    }
}
