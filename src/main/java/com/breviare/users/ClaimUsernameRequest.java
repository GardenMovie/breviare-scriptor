package com.breviare.users;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClaimUsernameRequest(
        @Size(min = 3, max = 32) @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String username
) {}
