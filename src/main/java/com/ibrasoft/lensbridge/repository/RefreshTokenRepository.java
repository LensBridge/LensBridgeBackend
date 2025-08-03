package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.auth.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, UUID> {
    
    Optional<RefreshToken> findByToken(String token);
    
    List<RefreshToken> findByUserId(UUID userId);
    
    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);
    
    void deleteByUserId(UUID userId);
    
    void deleteByToken(String token);
    
    void deleteByExpiryDateBefore(LocalDateTime dateTime);
    
    void deleteByUserIdAndRevokedTrue(UUID userId);
    
    long countByUserIdAndRevokedFalse(UUID userId);
}
