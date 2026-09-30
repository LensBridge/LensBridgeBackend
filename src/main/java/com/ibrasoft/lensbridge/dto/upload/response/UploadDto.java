package com.ibrasoft.lensbridge.dto.upload.response;

import com.ibrasoft.lensbridge.model.upload.UploadType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The user-facing view of an upload.
 * <p>
 * Two things about this DTO are security boundaries, not cosmetics:
 * <ul>
 *   <li>{@link #fileUrl} and {@link #thumbnailUrl} are <strong>absolute, expiring, presigned
 *       URLs</strong>, never the raw R2 object key. The bucket is fronted by a public custom
 *       domain, so a leaked key is an unauthenticated fetch of the media; and the presign step
 *       is where the approval gate is enforced. Both are {@code null} when the caller is not
 *       allowed to see the bytes (unapproved content, or a storage failure).</li>
 *   <li>{@link #uploadedBy} and {@link #instagramHandle} are {@code null} on anonymous uploads
 *       unless the caller is the uploader or holds {@code media:upload:read}. Anonymity was
 *       promised to the uploader at submit time; serializing the author id revokes it.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadDto {
    private UUID uuid;
    private String fileName;

    @Schema(description = "Absolute, expiring presigned URL for the media. Use verbatim -- do NOT "
            + "concatenate it with a media base URL. Null when the caller may not view the bytes.",
            example = "https://media.lensbridge.tech/images/e5c6f37c-...?X-Amz-Signature=...")
    private String fileUrl;

    @Schema(description = "Absolute, expiring presigned URL for the thumbnail. Falls back to the "
            + "full-size URL when no thumbnail exists. Null when the caller may not view the bytes.")
    private String thumbnailUrl;

    private String uploadDescription;

    @Schema(description = "Null on anonymous uploads unless the caller is the uploader or holds media:upload:read.")
    private String instagramHandle;

    @Schema(description = "Null on anonymous uploads unless the caller is the uploader or holds media:upload:read.")
    private UUID uploadedBy;

    private UUID eventId;
    private String eventName;
    private Instant createdDate;
    private boolean approved;
    private boolean featured;
    private boolean isAnon;
    private UploadType contentType;

    @Schema(description = "When fileUrl/thumbnailUrl stop working. Re-fetch the upload after this "
            + "instant rather than caching the URLs. Null when no URLs were issued.")
    private Instant urlExpiresAt;
}
