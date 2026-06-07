package com.breviare.links;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkRepository extends JpaRepository<Link, UUID> {
    Optional<Link> findByCode(String code);

    @Query("SELECT l FROM Link l WHERE l.owner.id = :ownerId AND (:includeExpired = true OR l.isExpired = false) ORDER BY l.createdAt DESC")
    List<Link> findByOwnerPaged(UUID ownerId, boolean includeExpired, Pageable pageable);

    @Modifying
    @Query("UPDATE Link l SET l.isExpired = true, l.expiredAt = :now WHERE l.isExpired = false AND l.absoluteExpiresAt < :now")
    int expireByAbsoluteTtl(Instant now);

    @Modifying
    @Query("UPDATE Link l SET l.isExpired = true, l.expiredAt = :now WHERE l.isExpired = false AND l.lastClickedAt < :cutoff")
    int expireByInactivity(Instant now, Instant cutoff);
}
