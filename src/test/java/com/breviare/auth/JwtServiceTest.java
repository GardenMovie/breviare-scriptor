package com.breviare.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 15, 30);
    }

    @Test
    void accessTokenRoundTripsToSameUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void refreshTokenRoundTripsToSameUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateRefreshToken(userId);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void expiredTokenIsNotValid() {
        JwtService shortLivedService = new JwtService(SECRET, 0, 0);
        String token = shortLivedService.generateAccessToken(UUID.randomUUID());

        // access expiry is minutes * 60 seconds; with 0 minutes the token expires immediately
        awaitClockTick();

        assertThat(shortLivedService.isValid(token)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService otherService = new JwtService("a-completely-different-secret-key-value-here", 15, 30);
        String token = otherService.generateAccessToken(UUID.randomUUID());

        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(UUID.randomUUID());
        String[] parts = token.split("\\.");
        // flip a character in the payload segment to invalidate the signature
        String tamperedPayload = flipLastChar(parts[1]);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(jwtService.isValid("not-a-valid-jwt")).isFalse();
    }

    @Test
    void blankTokenIsRejected() {
        assertThat(jwtService.isValid("")).isFalse();
    }

    private static void awaitClockTick() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String flipLastChar(String segment) {
        char last = segment.charAt(segment.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        return segment.substring(0, segment.length() - 1) + replacement;
    }
}
