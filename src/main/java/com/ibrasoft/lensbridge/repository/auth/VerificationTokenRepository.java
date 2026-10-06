package com.ibrasoft.lensbridge.repository.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;
import com.ibrasoft.lensbridge.model.auth.VerificationToken.TokenType;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    @Query("select v from VerificationToken v where v.tokenHash = :tokenHash and v.type = :type and v.usedAt is null and v.expiresAt > :now")
    Optional<VerificationToken> findValidToken(@Param("tokenHash") String tokenHash, @Param("type") TokenType type, @Param("now") Instant now);

    /**
     * Marks every still-unused token of this type for the user as used. Flushes first so a token
     * the caller has just marked is not overwritten, and does not clear the persistence context
     * because callers keep working with the user entity afterwards.
     */
    @Modifying(flushAutomatically = true)
    @Query("update VerificationToken v set v.usedAt = :now where v.user = :user and v.type = :type and v.usedAt is null")
    int markOutstandingUsed(@Param("user") User user, @Param("type") TokenType type, @Param("now") Instant now);

    @Modifying
    @Query("delete from VerificationToken v where v.usedAt is not null or v.expiresAt < :now")
    int deleteExpiredOrUsed(@Param("now") Instant now);
}
