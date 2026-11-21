package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Whether people may submit new media (POST /api/upload/{eventId}/direct/presign and
 * /complete). A deployment can close submissions while keeping everything that already exists:
 * reading uploads, moderation and the gallery are not affected.
 * <p>
 * {@code lensbridge.media-submissions.enabled}, or the environment variable
 * {@code LENSBRIDGE_MEDIA_SUBMISSIONS_ENABLED}; open unless set to false.
 */
@Component
public class MediaSubmissions {

    /** What a caller is told while submissions are closed. */
    public static final String CLOSED_MESSAGE =
            "Media submissions are closed for now. They will be back in a future update.";

    private final boolean open;

    public MediaSubmissions(
            @Value("${lensbridge.media-submissions.enabled:${LENSBRIDGE_MEDIA_SUBMISSIONS_ENABLED:true}}") boolean open) {
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }

    /** Throws a 503 with {@link #CLOSED_MESSAGE} while submissions are closed. */
    public void requireOpen() {
        if (!open) {
            throw new ApiResponseException(HttpStatus.SERVICE_UNAVAILABLE, new MessageResponse(CLOSED_MESSAGE));
        }
    }
}
