package com.breviare.blocklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BlocklistRepository extends JpaRepository<BlocklistDomain, String> {

    boolean existsByDomain(String domain);

    @Modifying
    @Query("DELETE FROM BlocklistDomain b WHERE b.source = :source")
    void deleteBySource(String source);
}
