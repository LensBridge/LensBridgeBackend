package com.ibrasoft.lensbridge.model.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {
    
    @Id
    private UUID id;
    
    @Indexed(unique = true)
    private String token;
    
    @Indexed
    private UUID userId;
    
    private LocalDateTime expiryDate;
    
    private LocalDateTime createdDate;
    
    private LocalDateTime lastUsedDate;
    
    private String deviceInfo;
    
    private String ipAddress; 
    
    private boolean revoked;
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}
