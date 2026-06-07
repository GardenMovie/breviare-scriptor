package com.breviare.links;

import jakarta.persistence.*;
import com.breviare.users.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 6)
    private String code;

    @Column(nullable = false)
    private String destination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant lastClickedAt = Instant.now();

    @Column(nullable = false)
    private int inactivityTtlDays = 30;

    private Instant absoluteExpiresAt;

    @Column(nullable = false)
    private long clickCount = 0;

    @Column(nullable = false)
    private boolean isExpired = false;

    private Instant expiredAt;

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastClickedAt() { return lastClickedAt; }
    public void setLastClickedAt(Instant lastClickedAt) { this.lastClickedAt = lastClickedAt; }
    public int getInactivityTtlDays() { return inactivityTtlDays; }
    public void setInactivityTtlDays(int inactivityTtlDays) { this.inactivityTtlDays = inactivityTtlDays; }
    public Instant getAbsoluteExpiresAt() { return absoluteExpiresAt; }
    public void setAbsoluteExpiresAt(Instant absoluteExpiresAt) { this.absoluteExpiresAt = absoluteExpiresAt; }
    public long getClickCount() { return clickCount; }
    public void setClickCount(long clickCount) { this.clickCount = clickCount; }
    public boolean isExpired() { return isExpired; }
    public void setExpired(boolean expired) { isExpired = expired; }
    public Instant getExpiredAt() { return expiredAt; }
    public void setExpiredAt(Instant expiredAt) { this.expiredAt = expiredAt; }
}
