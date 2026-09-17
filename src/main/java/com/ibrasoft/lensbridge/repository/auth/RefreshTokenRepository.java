package com.ibrasoft.lensbridge.repository.auth;

import com.ibrasoft.lensbridge.model.auth.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    
    List<RefreshToken> findByUser_Id(UUID userId);
    
    List<RefreshToken> findByUser_IdAndRevokedFalse(UUID userId);
    
    void deleteByUser_Id(UUID userId);
    
    void deleteByTokenHash(String tokenHash);
    
    void deleteByExpiryDateBefore(Instant dateTime);
    
    void deleteByUser_IdAndRevokedTrue(UUID userId);
    
    long countByUser_IdAndRevokedFalse(UUID userId);

    List<RefreshToken> findByRevokedTrueAndCreatedDateBefore(Instant minusDays);
}
