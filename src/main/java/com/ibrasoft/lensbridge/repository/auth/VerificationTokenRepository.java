package com.ibrasoft.lensbridge.repository.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    @Query("select v.user from VerificationToken v where v.tokenHash = :tokenHash")
    User findUserByToken(@Param("tokenHash") String tokenHash);

    @Query("select v from VerificationToken v where v.tokenHash = :tokenHash and v.usedAt is null and v.expiresAt > current_timestamp")
    Optional<VerificationToken> findValidToken(@Param("tokenHash") String tokenHash);

    @Query("select v from VerificationToken v where v.user = :user order by v.createdAt desc limit 1")
    Optional<VerificationToken> findLatestByUser(@Param("user") User user);
}
