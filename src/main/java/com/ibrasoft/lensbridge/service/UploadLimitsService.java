package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.dto.upload.response.UploadLimitsResponse;
import com.ibrasoft.lensbridge.exception.DailyLimitExceededException;
import com.ibrasoft.lensbridge.exception.FileSizeLimitExceededException;
import com.ibrasoft.lensbridge.exception.InvalidContentTypeException;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.security.services.AuthorityResolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadLimitsService {

    private final UploadProperties uploadProperties;
    private final UploadService uploadService;
    private final AuthorityResolver authorityResolver;

    /**
     * Pre-flight limits for the presign step.
     * <p>
     * {@code contentType} here is only ever a client-supplied string — no bytes exist yet — so
     * this check keeps obviously-wrong requests from getting a signed URL and nothing more. The
     * authoritative check is {@code ImageValidationService}, which sniffs the stored object at
     * completion time.
     */
    public void validateUpload(UUID userId, User user, long fileSize, String contentType) {
        String normalizedType = ImageValidationService.normalizeContentType(contentType);
        if (normalizedType == null || !uploadProperties.getAllowedFileTypes().contains(normalizedType)) {
            throw new InvalidContentTypeException(contentType);
        }

        String tier = resolveTier(user);

        DataSize maxAllowed = uploadProperties.getMaxSizeForRole(tier);
        if (fileSize > maxAllowed.toBytes()) {
            throw new FileSizeLimitExceededException(maxAllowed.toBytes(), fileSize);
        }

        int dailyLimit = uploadProperties.getDailyLimitForRole(tier);
        if (uploadService.hasReachedDailyLimit(userId, dailyLimit)) {
            long count = uploadService.countUploadsToday(userId);
            throw new DailyLimitExceededException(dailyLimit, count);
        }
    }

    public UploadLimitsResponse getLimitsForUser(User user) {
        String tier = resolveTier(user);
        DataSize maxSize = uploadProperties.getMaxSizeForRole(tier);
        int dailyLimit = uploadProperties.getDailyLimitForRole(tier);
        long uploadsToday = uploadService.countUploadsToday(user.getId());

        return UploadLimitsResponse.builder()
                .role(tier)
                .maxSizeBytes(maxSize.toBytes())
                .maxSizeMB(maxSize.toMegabytes())
                .allowedContentTypes(uploadProperties.getAllowedFileTypes())
                .dailyLimit(dailyLimit)
                .uploadsToday(uploadsToday)
                .uploadsRemaining(Math.max(0, dailyLimit - uploadsToday))
                .build();
    }

    // TODO: Hacky fix for now. Do less hacky thing later. Maybe.
    private String resolveTier(User user) {
        if (user.hasRole(Role.ROOT)) {
            return "root";
        }
        Set<Permission> permissions = authorityResolver.resolvePermissions(user);
        if (permissions.contains(Permission.MEDIA_UPLOAD_MODERATE)) {
            return "admin";
        }
        return "user";
    }
}
