package com.ibrasoft.lensbridge.repository.auth;

import com.ibrasoft.lensbridge.model.auth.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUser_IdAndRevokedFalse(UUID userId);

    long countByUser_IdAndRevokedFalse(UUID userId);

    /**
     * Revokes a token only if it is still active and returns the rows changed. The conditional
     * update is what makes rotation atomic: of two concurrent refreshes with the same token, the
     * database lets exactly one see a count of 1. The persistence context is cleared so later
     * reads in the same transaction see the revoked state.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.tokenHash = :tokenHash and t.revoked = false")
    int revokeIfActive(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllActiveForUser(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.user.id = :userId and t.expiryDate < :now")
    int deleteExpiredForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.expiryDate < :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.revoked = true and t.createdDate < :cutoff")
    int deleteRevokedCreatedBefore(@Param("cutoff") Instant cutoff);
}
