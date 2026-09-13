package com.seatlock.repository;

import com.seatlock.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.userId = :userId and r.revoked = false")
    void revokeAllForUser(@Param("userId") UUID userId);

    @Query("select r from RefreshToken r where r.userId = :userId and r.revoked = false "
            + "and r.expiresAt > :now order by r.lastUsedAt desc")
    List<RefreshToken> findActiveSessions(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true "
            + "where r.id = :id and r.userId = :userId and r.revoked = false")
    int revokeOne(@Param("id") UUID id, @Param("userId") UUID userId);
}
