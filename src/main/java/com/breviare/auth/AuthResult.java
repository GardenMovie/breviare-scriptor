package com.breviare.auth;

import com.breviare.users.User;

public record AuthResult(User user, String accessToken, String refreshToken) {
    public AuthResponse toResponse() {
        return new AuthResponse(
                new AuthResponse.UserSummary(user.getId().toString(), user.getEmail(), user.getUsername()),
                accessToken
        );
    }
}
