package com.breviare.blocklist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class BlocklistSyncService {

    private static final Logger log = LoggerFactory.getLogger(BlocklistSyncService.class);
    private static final String SOURCE = "hagezi-pro";
    private static final String HAGEZI_PRO_URL =
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/pro.txt";
    private static final int BATCH_SIZE = 500;

    private final BlocklistRepository blocklistRepository;
    private final RestClient restClient;

    public BlocklistSyncService(BlocklistRepository blocklistRepository, RestClient.Builder restClientBuilder) {
        this.blocklistRepository = blocklistRepository;
        this.restClient = restClientBuilder.build();
    }

    @Scheduled(cron = "0 0 3 * * *") // 03:00 UTC daily
    @Transactional
    public void sync() {
        log.info("Starting blocklist sync from {}", SOURCE);
        String raw;
        try {
            raw = restClient.get().uri(HAGEZI_PRO_URL).retrieve().body(String.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch blocklist from {}: {}", HAGEZI_PRO_URL, e.getMessage());
            return;
        }

        if (raw == null || raw.isBlank()) {
            log.warn("Blocklist response was empty, skipping sync");
            return;
        }

        List<BlocklistDomain> entries = Arrays.stream(raw.split("\n"))
                .map(String::strip)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(domain -> domain.toLowerCase().replaceFirst("^www\\.", ""))
                .distinct()
                .map(domain -> new BlocklistDomain(domain, SOURCE, Instant.now()))
                .toList();

        blocklistRepository.deleteBySource(SOURCE);

        for (int i = 0; i < entries.size(); i += BATCH_SIZE) {
            blocklistRepository.saveAll(entries.subList(i, Math.min(i + BATCH_SIZE, entries.size())));
        }

        log.info("Blocklist sync complete: {} domains loaded", entries.size());
    }
}
