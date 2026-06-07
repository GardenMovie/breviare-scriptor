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

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String username;

    // user can only change their username once a month
    private Instant usernameChangedAt;

    private String vanityDestination;

    @Column(nullable = false)
    private int vanityDestinationChangeCountThisMonth = 0;

    // For the destination we only care about how many were changed this month
    private Instant vanityDestinationChangedAt;

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
    public String getVanityDestination() { return vanityDestination; }
    public void setVanityDestination(String vanityDestination) { this.vanityDestination = vanityDestination; }
    public int getVanityDestinationChangeCountThisMonth() { return vanityDestinationChangeCountThisMonth; }
    public void setVanityDestinationChangeCountThisMonth(int count) { this.vanityDestinationChangeCountThisMonth = count; }
    public Instant getVanityDestinationChangedAt() { return vanityDestinationChangedAt; }
    public void setVanityDestinationChangedAt(Instant t) { this.vanityDestinationChangedAt = t; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
