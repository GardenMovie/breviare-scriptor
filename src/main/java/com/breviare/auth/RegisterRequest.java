package com.breviare.auth;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank @Size(min = 3, max = 32) @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String username
) {}
