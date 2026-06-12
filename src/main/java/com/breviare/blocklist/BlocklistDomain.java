package com.breviare.blocklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "blocklist_domains")
public class BlocklistDomain {

    @Id
    @Column(length = 253)
    private String domain;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public BlocklistDomain() {}

    public BlocklistDomain(String domain, String source, Instant syncedAt) {
        this.domain = domain;
        this.source = source;
        this.syncedAt = syncedAt;
    }

    public String getDomain() { return domain; }
    public String getSource() { return source; }
    public Instant getSyncedAt() { return syncedAt; }
}
