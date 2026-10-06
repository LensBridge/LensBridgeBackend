package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.dto.upload.response.UploadLimitsResponse;
import com.ibrasoft.lensbridge.exception.DailyLimitExceededException;
import com.ibrasoft.lensbridge.exception.FileSizeLimitExceededException;
import com.ibrasoft.lensbridge.exception.InvalidContentTypeException;
import com.ibrasoft.lensbridge.model.auth.Role;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UploadLimitsService {

    private final UploadProperties uploadProperties;
    private final UploadService uploadService;

    /**
     * The role whose upload limits apply: ROOT, then ADMIN, otherwise USER.
     * <p>
     * Deliberately not "highest enum ordinal": the board roles sit after ADMIN in {@link Role}
     * but have no upload limits of their own, so an admin who is also a board editor would
     * otherwise be throttled like a plain user.
     */
    public Role getHighestRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Role.USER;
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (authorities.contains(Role.ROOT.getAuthority())) return Role.ROOT;
        if (authorities.contains(Role.ADMIN.getAuthority())) return Role.ADMIN;
        return Role.USER;
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
            throw new DailyLimitExceededException(dailyLimit, count, role.name().toLowerCase());
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
