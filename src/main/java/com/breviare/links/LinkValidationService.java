package com.breviare.links;

import com.breviare.blocklist.BlocklistRepository;
import com.breviare.common.BreviareException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

@Service
public class LinkValidationService {

    private static final Logger log = LoggerFactory.getLogger(LinkValidationService.class);
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final String SAFE_BROWSING_URL =
            "https://safebrowsing.googleapis.com/v4/threatMatches:find?key=";

    private final RestClient restClient;
    private final BlocklistRepository blocklistRepository;
    private final String safeBrowsingApiKey;

    public LinkValidationService(
            RestClient.Builder restClientBuilder,
            BlocklistRepository blocklistRepository,
            @Value("${GOOGLE_SAFE_BROWSING_API_KEY:}") String safeBrowsingApiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.blocklistRepository = blocklistRepository;
        this.safeBrowsingApiKey = safeBrowsingApiKey;
    }

    public void validate(String destination) {
        URI uri = parseAndCheckScheme(destination);
        checkBlocklist(uri.getHost());
        checkSafeBrowsing(destination);
    }

    private URI parseAndCheckScheme(String destination) {
        URI uri;
        try {
            uri = new URI(destination);
        } catch (URISyntaxException e) {
            throw BreviareException.badRequest("Invalid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw BreviareException.badRequest("Only http and https URLs are allowed");
        }
        return uri;
    }

    private void checkBlocklist(String host) {
        if (host == null) return;
        String normalised = host.toLowerCase().replaceFirst("^www\\.", "");
        if (blocklistRepository.existsByDomain(normalised)) {
            throw BreviareException.unprocessableEntity("URL is not allowed");
        }
    }

    private void checkSafeBrowsing(String destination) {
        if (safeBrowsingApiKey == null || safeBrowsingApiKey.isBlank()) {
            log.debug("GOOGLE_SAFE_BROWSING_API_KEY not configured — skipping Safe Browsing check");
            return;
        }

        SafeBrowsingRequest body = new SafeBrowsingRequest(
                new SafeBrowsingRequest.ClientInfo("breviare", "1.0"),
                new SafeBrowsingRequest.ThreatInfo(
                        List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                        List.of("ANY_PLATFORM"),
                        List.of("URL"),
                        List.of(new SafeBrowsingRequest.ThreatEntry(destination))
                )
        );

        try {
            SafeBrowsingResponse response = restClient.post()
                    .uri(SAFE_BROWSING_URL + safeBrowsingApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(SafeBrowsingResponse.class);

            if (response != null && response.matches() != null && !response.matches().isEmpty()) {
                throw BreviareException.unprocessableEntity("URL flagged as unsafe");
            }
        } catch (BreviareException e) {
            throw e;
        } catch (RestClientException e) {
            // Safe Browsing API unavailable — fail open to avoid blocking legitimate links
            log.warn("Safe Browsing API call failed, failing open: {}", e.getMessage());
        }
    }

    // Safe Browsing API request/response shapes

    record SafeBrowsingRequest(ClientInfo client, ThreatInfo threatInfo) {
        record ClientInfo(String clientId, String clientVersion) {}
        record ThreatInfo(
                List<String> threatTypes,
                List<String> platformTypes,
                List<String> threatEntryTypes,
                List<ThreatEntry> threatEntries
        ) {}
        record ThreatEntry(String url) {}
    }

    record SafeBrowsingResponse(List<ThreatMatch> matches) {
        record ThreatMatch(String threatType, String platformType, ThreatEntry threat) {}
        record ThreatEntry(String url) {}
    }
}
