package com.breviare.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private static final int CAPACITY = 20;

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        chain = mock(FilterChain.class);
    }

    @Test
    void getRequestsAreNeverRateLimitedEvenAfterManyCalls() throws Exception {
        for (int i = 0; i < CAPACITY + 10; i++) {
            MockHttpServletRequest request = mutatingRequest("GET", "10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
        verify(chain, times(CAPACITY + 10)).doFilter(any(), any());
    }

    @Test
    void firstNRequestsWithinCapacityPassThrough() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletRequest request = mutatingRequest("POST", "10.0.0.2");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void requestBeyondCapacityIsRejectedWithRetryAfter() throws Exception {
        String ip = "10.0.0.3";
        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilterInternal(mutatingRequest("POST", ip), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(mutatingRequest("POST", ip), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void differentIpsGetIndependentBuckets() throws Exception {
        String exhaustedIp = "10.0.0.4";
        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilterInternal(mutatingRequest("POST", exhaustedIp), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse exhaustedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(mutatingRequest("POST", exhaustedIp), exhaustedResponse, chain);
        assertThat(exhaustedResponse.getStatus()).isEqualTo(429);

        MockHttpServletResponse freshResponse = new MockHttpServletResponse();
        filter.doFilterInternal(mutatingRequest("POST", "10.0.0.5"), freshResponse, chain);
        assertThat(freshResponse.getStatus()).isNotEqualTo(429);
    }

    @Test
    void deleteAndPatchAreAlsoRateLimited() throws Exception {
        String ip = "10.0.0.6";
        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilterInternal(mutatingRequest("DELETE", ip), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(mutatingRequest("PATCH", ip), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void spoofedForwardedForHeaderResetsTheLimit_knownBypass() throws Exception {
        // Documents the known trust issue: X-Forwarded-For is trusted verbatim with
        // no proxy allowlist, so a client can spoof a fresh IP per request to dodge
        // the limiter entirely. This test asserts current (buggy) behavior.
        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilterInternal(mutatingRequest("POST", "10.0.0.7"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(mutatingRequest("POST", "10.0.0.7"), blockedResponse, chain);
        assertThat(blockedResponse.getStatus()).isEqualTo(429);

        MockHttpServletRequest spoofed = new MockHttpServletRequest();
        spoofed.setMethod("POST");
        spoofed.addHeader("X-Forwarded-For", "1.2.3." + System.nanoTime() % 256);
        MockHttpServletResponse spoofedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(spoofed, spoofedResponse, chain);

        assertThat(spoofedResponse.getStatus()).isNotEqualTo(429);
    }

    @Test
    void missingForwardedForFallsBackToRemoteAddr() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRemoteAddr("192.168.1.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    private static MockHttpServletRequest mutatingRequest(String method, String forwardedForIp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.addHeader("X-Forwarded-For", forwardedForIp);
        return request;
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
