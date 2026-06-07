package com.breviare.users;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String username;

    private Instant usernameChangedAt;

    @Column(nullable = false)
    private int usernameChangeCountThisMonth = 0;

    private String vanityDestination;

    @Column(nullable = false)
    private int vanityDestinationChangeCountThisMonth = 0;

    private Instant vanityDestinationChangedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Instant getUsernameChangedAt() { return usernameChangedAt; }
    public void setUsernameChangedAt(Instant usernameChangedAt) { this.usernameChangedAt = usernameChangedAt; }
    public int getUsernameChangeCountThisMonth() { return usernameChangeCountThisMonth; }
    public void setUsernameChangeCountThisMonth(int count) { this.usernameChangeCountThisMonth = count; }
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
