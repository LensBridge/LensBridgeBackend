package com.ibrasoft.lensbridge.repository.auth;

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
}
