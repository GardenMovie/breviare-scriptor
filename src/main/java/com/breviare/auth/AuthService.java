package com.breviare.auth;

import com.breviare.users.User;
import com.breviare.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.breviare.common.BreviareException;

import java.util.UUID;

@Service
public class AuthService {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthService(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public AuthResult issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        return new AuthResult(user, accessToken, refreshToken);
    }

    public AuthResponse refresh(String refreshToken) {
        if (refreshToken == null || !jwtService.isValid(refreshToken)) {
            throw new BreviareException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid or missing refresh token");
        }
        UUID userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BreviareException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid or missing refresh token"));
        String accessToken = jwtService.generateAccessToken(userId);
        return new AuthResponse(
                new AuthResponse.UserSummary(user.getId().toString(), user.getEmail(), user.getUsername()),
                accessToken
        );
    }
}
