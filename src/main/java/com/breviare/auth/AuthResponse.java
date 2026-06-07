package com.breviare.auth;

public record AuthResponse(UserSummary user, String accessToken) {
    public record UserSummary(String id, String email, String username) {}
}
