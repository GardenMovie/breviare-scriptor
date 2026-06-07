package com.breviare.links;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

public record CreateLinkRequest(
        @NotBlank @URL String destination,
        @Positive Integer inactivityTtlDays,
        Instant absoluteExpiresAt
) {}
