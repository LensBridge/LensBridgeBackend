package com.ibrasoft.lensbridge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class R2StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    @Value("${cloudflare.r2.public-url:}")
    private String publicUrl;

    @Value("${cloudflare.r2.url-expiration-minutes:15}")
    private long urlExpirationMinutes;

    /**
     * Upload an object directly to R2.
     *
     * This should generally only be used internally
     * for backend-generated objects like thumbnails,
     * transcoded media, exports, etc.
     *
     * User uploads should use presigned URLs instead.
     */
    public void putObject(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) {

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentLength(contentLength)
                .contentType(contentType)
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromInputStream(inputStream, contentLength)
        );

        log.info("Uploaded object to R2: {}", objectKey);
    }

    /**
     * Generate a presigned upload URL.
     *
     * The client uploads directly to R2 using this URL.
     */
    public String generatePresignedUploadUrl(
            String objectKey,
            String contentType,
            long contentLength,
            Duration expiration
    ) {

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(expiration)
                        .putObjectRequest(putRequest)
                        .build();

        String url = presigner
                .presignPutObject(presignRequest)
                .url()
                .toString();

        log.debug("Generated presigned upload URL for {}", objectKey);

        return url;
    }

    /**
     * Convenience overload using configured expiration.
     */
    public String generatePresignedUploadUrl(
            String objectKey,
            String contentType,
            long contentLength
    ) {

        return generatePresignedUploadUrl(
                objectKey,
                contentType,
                contentLength,
                Duration.ofMinutes(urlExpirationMinutes)
        );
    }

    /**
     * Generate a secure temporary download URL.
     */
    public String generatePresignedDownloadUrl(
            String objectKey
    ) {

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(
                                Duration.ofMinutes(urlExpirationMinutes)
                        )
                        .getObjectRequest(getRequest)
                        .build();

        String url = presigner
                .presignGetObject(presignRequest)
                .url()
                .toString();

        return rewriteToCustomDomain(url);
    }

    /**
     * Delete an object.
     */
    public void deleteObject(String objectKey) {

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        s3Client.deleteObject(request);

        log.info("Deleted object from R2: {}", objectKey);
    }

    /**
     * Check whether an object exists.
     */
    public boolean objectExists(String objectKey) {

        try {

            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.headObject(request);

            return true;

        } catch (NoSuchKeyException e) {

            return false;

        } catch (Exception e) {

            log.error(
                    "Failed checking object existence: {}",
                    objectKey,
                    e
            );

            return false;
        }
    }

    /**
     * Retrieve object metadata.
     */
    public R2ObjectMetadata getObjectMetadata(String objectKey) {

        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        HeadObjectResponse response =
                s3Client.headObject(request);

        return new R2ObjectMetadata(
                objectKey,
                response.contentLength(),
                response.contentType(),
                response.eTag(),
                response.lastModified(),
                response.metadata()
        );
    }

    /**
     * Extract object key from URL or return original.
     */
    public String extractObjectKey(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        if (!value.startsWith("http")) {
            return value;
        }

        try {

            URI uri = URI.create(value);

            String path = uri.getPath();

            if (path == null || path.isBlank()) {
                return null;
            }

            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            if (path.startsWith(bucketName + "/")) {
                path = path.substring(bucketName.length() + 1);
            }

            return path;

        } catch (Exception e) {

            log.error("Failed extracting object key", e);

            return null;
        }
    }

    /**
     * Replace R2 endpoint with public CDN/custom domain.
     */
    private String rewriteToCustomDomain(String originalUrl) {

        if (publicUrl == null || publicUrl.isBlank()) {
            return originalUrl;
        }

        try {

            URI originalUri = URI.create(originalUrl);
            URI publicUri = URI.create(publicUrl);

            String path = originalUri.getPath();

            if (path.startsWith("/" + bucketName + "/")) {
                path = path.substring(bucketName.length() + 1);
            }

            StringBuilder rewritten = new StringBuilder();

            rewritten.append(publicUri.getScheme())
                    .append("://")
                    .append(publicUri.getHost())
                    .append(path);

            if (originalUri.getQuery() != null) {
                rewritten.append("?")
                        .append(originalUri.getQuery());
            }

            return rewritten.toString();

        } catch (Exception e) {

            log.error(
                    "Failed rewriting presigned URL to custom domain",
                    e
            );

            return originalUrl;
        }
    }

    /**
     * Upload an image file from a MultipartFile directly to R2.
     * Used for backend-initiated uploads (e.g. poster images).
     */
    public String uploadImage(String key, MultipartFile file) throws IOException {
        putObject(key, file.getInputStream(), file.getSize(), file.getContentType());
        return key;
    }

    /**
     * Download object bytes from R2.
     */
    public byte[] getObjectBytes(String objectKey) throws IOException {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(getRequest)) {
            return stream.readAllBytes();
        }
    }

    /**
     * Compute the SHA-256 hex digest of a stored object.
     */
    public String calculateSha256Hash(String objectKey) throws Exception {
        byte[] bytes = getObjectBytes(objectKey);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    /**
     * Generate a presigned download URL for an object, with access control.
     * Throws SecurityException if the content is unapproved and the caller is not admin.
     */
    public String getSecureUrl(String objectKey, boolean approved, boolean isAdmin) {
        if (!approved && !isAdmin) {
            throw new SecurityException("Access denied: content is not approved");
        }
        return generatePresignedDownloadUrl(objectKey);
    }

    /**
     * Generate a presigned download URL for a thumbnail, with access control.
     */
    public String getSecureThumbnailUrl(String thumbnailKey, boolean approved, boolean isAdmin) {
        if (!approved && !isAdmin) {
            throw new SecurityException("Access denied: content is not approved");
        }
        return generatePresignedDownloadUrl(thumbnailKey);
    }

    /**
     * Lightweight immutable metadata DTO.
     */
    public record R2ObjectMetadata(
            String objectKey,
            long contentLength,
            String contentType,
            String etag,
            java.time.Instant lastModified,
            Map<String, String> metadata
    ) {}
}