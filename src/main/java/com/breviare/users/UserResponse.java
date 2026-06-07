package com.breviare.users;

import java.time.Instant;

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
                Math.max(0, 1 - user.getUsernameChangeCountThisMonth()),
                user.getVanityDestinationChangedAt(),
                Math.max(0, 5 - user.getVanityDestinationChangeCountThisMonth()),
                user.getCreatedAt()
        );
    }
}
