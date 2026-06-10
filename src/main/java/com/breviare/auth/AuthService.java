package com.breviare.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.breviare.common.BreviareException;

@Service
public class AuthService {

    private final JwtService jwtService;

    public AuthService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    // Password auth was removed in favour of Google sign-in, which is not wired up yet.
    // These endpoints are intentionally stubbed until Google ID-token verification lands.
    public AuthResult register(RegisterRequest request) {
        throw new BreviareException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED",
                "Registration is being migrated to Google sign-in and is temporarily unavailable");
    }

    public AuthResult login(LoginRequest request) {
        throw new BreviareException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED",
                "Password login has been removed; Google sign-in is not available yet");
    }

    public String refresh(String refreshToken) {
        if (refreshToken == null || !jwtService.isValid(refreshToken)) {
            throw new BreviareException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid or missing refresh token");
        }
        return jwtService.generateAccessToken(jwtService.extractUserId(refreshToken));
    }
}
