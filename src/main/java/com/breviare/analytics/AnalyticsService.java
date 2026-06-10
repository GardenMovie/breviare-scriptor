package com.breviare.analytics;

import com.breviare.links.Link;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Async
    @Transactional
    public void recordClick(Link link, HttpServletRequest request) {
        String ip = resolveClientIp(request);
        String ipHash = ip != null ? sha256(ip) : null;
        String referrer = request.getHeader("Referer");
        String userAgent = request.getHeader("User-Agent");

        // TODO: resolve countryCode from ip via GeoIP lookup instead of passing null
        analyticsRepository.save(new AnalyticsEvent(link, referrer, userAgent, ipHash, null));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
