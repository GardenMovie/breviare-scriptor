package com.breviare.auth;

import com.breviare.users.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.breviare.common.BreviareException;

@Service
public class AuthService {

    private final JwtService jwtService;

    public AuthService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public AuthResult issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        return new AuthResult(user, accessToken, refreshToken);
    }

    public String refresh(String refreshToken) {
        if (refreshToken == null || !jwtService.isValid(refreshToken)) {
            throw new BreviareException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid or missing refresh token");
        }
        return jwtService.generateAccessToken(jwtService.extractUserId(refreshToken));
    }
}
