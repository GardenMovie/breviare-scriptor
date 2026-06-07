package com.breviare.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.breviare.common.BreviaException;
import com.breviare.users.User;
import com.breviare.users.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw BreviaException.conflict("Email already in use");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw BreviaException.conflict("Username already taken");
        }

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUsername(request.username());
        userRepository.save(user);

        return new AuthResult(user, jwtService.generateAccessToken(user.getId()), jwtService.generateRefreshToken(user.getId()));
    }

    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new BreviaException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BreviaException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials");
        }

        return new AuthResult(user, jwtService.generateAccessToken(user.getId()), jwtService.generateRefreshToken(user.getId()));
    }

    public String refresh(String refreshToken) {
        if (refreshToken == null || !jwtService.isValid(refreshToken)) {
            throw new BreviaException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid or missing refresh token");
        }
        return jwtService.generateAccessToken(jwtService.extractUserId(refreshToken));
    }
}
