package com.breviare.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    // Atomic bump: concurrent redirects to the same vanity link would lose increments under a
    // read-modify-write, and this runs on every redirect.
    @Modifying
    @Query("UPDATE User u SET u.vanityClickCount = u.vanityClickCount + 1 WHERE u.id = :userId")
    void incrementVanityClickCount(UUID userId);
}
