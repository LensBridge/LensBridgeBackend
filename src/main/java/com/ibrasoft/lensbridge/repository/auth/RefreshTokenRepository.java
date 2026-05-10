package com.ibrasoft.lensbridge.repository.auth;

import com.ibrasoft.lensbridge.model.auth.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    
    Optional<RefreshToken> findByToken(String token);
    
    List<RefreshToken> findByUserId(UUID userId);
    
    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);
    
    void deleteByUserId(UUID userId);
    
    void deleteByToken(String token);
    
    void deleteByExpiryDateBefore(LocalDateTime dateTime);
    
    void deleteByUserIdAndRevokedTrue(UUID userId);
    
    long countByUserIdAndRevokedFalse(UUID userId);

    List<RefreshToken> findByRevokedTrueAndCreatedDateBefore(LocalDateTime minusDays);
}
