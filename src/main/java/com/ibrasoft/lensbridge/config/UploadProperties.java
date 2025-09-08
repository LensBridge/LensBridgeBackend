package com.ibrasoft.lensbridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for file uploads.
 */
@Component
@ConfigurationProperties(prefix = "uploads")
@Data
public class UploadProperties {
    
    /**
     * Maximum video duration in seconds
     */
    private int videoMaxduration = 240;
    
    /**
     * Global maximum file size (fallback)
     */
    @DataSizeUnit(DataUnit.BYTES)
    private DataSize globalMaxSize = DataSize.ofGigabytes(1);
    
    /**
     * Per-role maximum file sizes
     */
    private Map<String, DataSize> maxSize = new HashMap<>();
    
    /**
     * Allowed file types (MIME types)
     */
    private List<String> allowedFileTypes = List.of(
        "video/mp4", "video/quicktime", "video/x-matroska", "video/x-msvideo",
        "image/jpeg", "image/png", "image/heic"
    );
    
    /**
     * Get the maximum allowed file size for a specific role.
     * Falls back to user limit if role not found.
     */
    public DataSize getMaxSizeForRole(String role) {
        String roleKey = role;
        if (roleKey.startsWith("ROLE_")) {
            roleKey = roleKey.substring(5).toLowerCase();
        }
        
        DataSize roleLimit = maxSize.get(roleKey);
        if (roleLimit != null) {
            return roleLimit;
        }
        
        // Fallback to user limit, then global limit
        DataSize userLimit = maxSize.get("user");
        return userLimit != null ? userLimit : globalMaxSize;
    }
}
