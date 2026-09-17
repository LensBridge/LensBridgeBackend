package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.dto.upload.response.UploadLimitsResponse;
import com.ibrasoft.lensbridge.exception.DailyLimitExceededException;
import com.ibrasoft.lensbridge.exception.FileSizeLimitExceededException;
import com.ibrasoft.lensbridge.exception.InvalidContentTypeException;
import com.ibrasoft.lensbridge.model.auth.Role;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadLimitsService {

    private final UploadProperties uploadProperties;
    private final UploadService uploadService;

    public Role getHighestRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Role.USER;
        }
        return authentication.getAuthorities().stream()
                .map(a -> {
                    String name = a.getAuthority();
                    if (name.startsWith("ROLE_")) name = name.substring(5);
                    try { return Role.valueOf(name); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(Role.USER);
    }

    public void validateUpload(UUID userId, Role role, long fileSize, String contentType) {
        if (!uploadProperties.getAllowedFileTypes().contains(contentType)) {
            throw new InvalidContentTypeException(contentType);
        }

        DataSize maxAllowed = uploadProperties.getMaxSizeForRole(role.name().toLowerCase());
        if (fileSize > maxAllowed.toBytes()) {
            throw new FileSizeLimitExceededException(maxAllowed.toBytes(), fileSize);
        }

        int dailyLimit = uploadProperties.getDailyLimitForRole(role.name().toLowerCase());
        if (uploadService.hasReachedDailyLimit(userId, dailyLimit)) {
            long count = uploadService.countUploadsToday(userId);
            throw new DailyLimitExceededException(dailyLimit, count);
        }
    }

    public UploadLimitsResponse getLimitsForRole(Role role, UUID userId) {
        String roleKey = role.name().toLowerCase();
        DataSize maxSize = uploadProperties.getMaxSizeForRole(roleKey);
        int dailyLimit = uploadProperties.getDailyLimitForRole(roleKey);
        long uploadsToday = uploadService.countUploadsToday(userId);

        return UploadLimitsResponse.builder()
                .role(roleKey)
                .maxSizeBytes(maxSize.toBytes())
                .maxSizeMB(maxSize.toMegabytes())
                .allowedContentTypes(uploadProperties.getAllowedFileTypes())
                .dailyLimit(dailyLimit)
                .uploadsToday(uploadsToday)
                .uploadsRemaining(Math.max(0, dailyLimit - uploadsToday))
                .build();
    }
}
