package com.breviare.links;

import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

public record UpdateLinkRequest(
        @URL String destination,
        @Positive Integer inactivityTtlDays,
        Instant absoluteExpiresAt
) {}
