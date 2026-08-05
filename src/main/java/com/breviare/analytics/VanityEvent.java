package com.breviare.analytics;

import jakarta.persistence.*;
import com.breviare.users.User;

import java.time.Instant;

@Entity
@Table(name = "vanity_events")
public class VanityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Keyed on the user rather than a links row: vanity destinations can be cleared and re-pointed,
    // and the click history should outlive those changes.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant clickedAt = Instant.now();

    private String referrer;
    private String userAgent;

    @Column(length = 64)
    private String ipHash;

    @Column(length = 2)
    private String countryCode;

    public VanityEvent() {}

    public VanityEvent(User user, String referrer, String userAgent, String ipHash, String countryCode) {
        this.user = user;
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.ipHash = ipHash;
        this.countryCode = countryCode;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Instant getClickedAt() { return clickedAt; }
    public String getReferrer() { return referrer; }
    public String getUserAgent() { return userAgent; }
    public String getIpHash() { return ipHash; }
    public String getCountryCode() { return countryCode; }
}
