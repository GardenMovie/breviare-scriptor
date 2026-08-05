package com.breviare.users;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    // we will use google auth, no email password
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true, columnDefinition = "citext")
    private String username;

    // user can only change their username once a month
    private Instant usernameChangedAt;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    private String vanityDestination;

    @Column(nullable = false)
    private int vanityDestinationChangeCountThisMonth = 0;

    // For the destination we only care about how many were changed this month, maybe change so it a record of the first change in the 30 day period
    private Instant vanityDestinationChangedAt;

    // Lifetime total, bumped inline on each vanity redirect. Deliberately not derived from
    // vanity_daily_clicks, which only retains the rollup window used for charting.
    @Column(nullable = false)
    private long vanityClickCount = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Instant getUsernameChangedAt() { return usernameChangedAt; }
    public void setUsernameChangedAt(Instant usernameChangedAt) { this.usernameChangedAt = usernameChangedAt; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderUserId() { return providerUserId; }
    public void setProviderUserId(String providerUserId) { this.providerUserId = providerUserId; }
    public String getVanityDestination() { return vanityDestination; }
    public void setVanityDestination(String vanityDestination) { this.vanityDestination = vanityDestination; }
    public int getVanityDestinationChangeCountThisMonth() { return vanityDestinationChangeCountThisMonth; }
    public void setVanityDestinationChangeCountThisMonth(int count) { this.vanityDestinationChangeCountThisMonth = count; }
    public Instant getVanityDestinationChangedAt() { return vanityDestinationChangedAt; }
    public void setVanityDestinationChangedAt(Instant t) { this.vanityDestinationChangedAt = t; }
    public long getVanityClickCount() { return vanityClickCount; }
    public void setVanityClickCount(long vanityClickCount) { this.vanityClickCount = vanityClickCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
