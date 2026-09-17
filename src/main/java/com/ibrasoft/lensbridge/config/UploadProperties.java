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

@Component
@ConfigurationProperties(prefix = "uploads")
@Data
public class UploadProperties {

    @DataSizeUnit(DataUnit.BYTES)
    private DataSize globalMaxSize;

    private Map<String, DataSize> maxSize = new HashMap<>();

    private List<String> allowedFileTypes;

    private Map<String, Integer> dailyLimit = new HashMap<>();

    private int globalDailyLimit;

    public DataSize getMaxSizeForRole(String role) {
        String key = normalizeRoleKey(role);
        DataSize roleLimit = maxSize.get(key);
        if (roleLimit != null) return roleLimit;
        DataSize userLimit = maxSize.get("user");
        return userLimit != null ? userLimit : globalMaxSize;
    }

    public int getDailyLimitForRole(String role) {
        String key = normalizeRoleKey(role);
        Integer roleLimit = dailyLimit.get(key);
        if (roleLimit != null) return roleLimit;
        Integer userLimit = dailyLimit.get("user");
        return userLimit != null ? userLimit : globalDailyLimit;
    }

    private String normalizeRoleKey(String role) {
        if (role == null) return "user";
        String key = role.startsWith("ROLE_") ? role.substring(5) : role;
        return key.toLowerCase();
    }
}
