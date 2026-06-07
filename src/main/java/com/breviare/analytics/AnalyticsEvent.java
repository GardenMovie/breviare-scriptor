package com.breviare.analytics;

import jakarta.persistence.*;
import com.breviare.links.Link;

import java.time.Instant;

@Entity
@Table(name = "analytics_events")
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id", nullable = false)
    private Link link;

    @Column(nullable = false)
    private Instant clickedAt = Instant.now();

    private String referrer;
    private String userAgent;

    @Column(length = 64)
    private String ipHash;

    @Column(length = 2)
    private String countryCode;

    public AnalyticsEvent() {}

    public AnalyticsEvent(Link link, String referrer, String userAgent, String ipHash, String countryCode) {
        this.link = link;
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.ipHash = ipHash;
        this.countryCode = countryCode;
    }

    public Long getId() { return id; }
    public Link getLink() { return link; }
    public Instant getClickedAt() { return clickedAt; }
    public String getReferrer() { return referrer; }
    public String getUserAgent() { return userAgent; }
    public String getIpHash() { return ipHash; }
    public String getCountryCode() { return countryCode; }
}
