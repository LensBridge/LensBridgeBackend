package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.exception.ImageProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class R2StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final MediaConversionService mediaConversionService;

    @Value("${cloudflare.r2.access-key-id}")
    private String accessKeyId;

    @Value("${cloudflare.r2.secret-access-key}")
    private String secretAccessKey;

    @Value("${cloudflare.r2.endpoint}")
    private String endpoint;

    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    @Value("${cloudflare.r2.url-expiration-minutes:15}")
    private long urlExpirationMinutes;

    @PostConstruct
    public void init() {
        log.info("R2StorageService initialized (endpoint='{}', bucket='{}', publicUrl='{}')", endpoint, bucketName, publicUrl);
    }

    private String normalizeEndpoint(String ep) {
        if (ep == null) return null;
        String trimmed = ep.trim();
        // Remove any trailing slash
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Replace the CloudFlare R2 endpoint in a presigned URL with our custom domain
     */
    private String replaceEndpointWithCustomDomain(String presignedUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return presignedUrl; // Return original URL if no custom domain configured
        }
        
        try {
            URI originalUri = URI.create(presignedUrl);
            String originalHost = originalUri.getHost();
            String originalPath = originalUri.getPath();
            String queryString = originalUri.getQuery();
            
            // Extract the custom domain from publicUrl
            URI customUri = URI.create(publicUrl);
            String customHost = customUri.getHost();
            String customScheme = customUri.getScheme();
            
            // Remove bucket name from path if it exists
            String newPath = originalPath;
            if (originalPath != null && originalPath.startsWith("/" + bucketName + "/")) {
                newPath = originalPath.substring(bucketName.length() + 1); // Remove "/bucketName"
            }
            
            // Construct the new URL with custom domain
            StringBuilder customUrl = new StringBuilder();
            customUrl.append(customScheme).append("://").append(customHost);
            customUrl.append(newPath);
            if (queryString != null && !queryString.isEmpty()) {
                customUrl.append("?").append(queryString);
            }
            
            String result = customUrl.toString();
            log.debug("Replaced CloudFlare endpoint '{}' with custom domain '{}' and removed bucket from path: '{}' -> '{}'", 
                     originalHost, customHost, originalPath, newPath);
            return result;
        } catch (Exception e) {
            log.error("Failed to replace endpoint with custom domain in URL: {}", presignedUrl, e);
            return presignedUrl; // Return original URL on error
        }
    }


    /**
     * Upload an image file to R2
     */
    public String uploadImage(File imageFile, String fileName) throws IOException {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".heic") || lowerName.endsWith(".heif")) {
            // Convert HEIC to JPG before uploading
            File jpgFile = File.createTempFile("converted-", ".jpg");
            try {
                MediaConversionService.convertHeicToJpg(imageFile, jpgFile);
                String jpgName = fileName.replaceAll("(?i)\\.heic$", ".jpg");
                String result = uploadFile(jpgFile, jpgName, "images/");
                jpgFile.delete();
                return result;
            } catch (Exception e) {
                jpgFile.delete();
                log.error("HEIC to JPG conversion failed", e);
                throw new IOException("HEIC to JPG conversion failed", e);
            }
        }
        return uploadFile(imageFile, fileName, "images/");
    }

    /**
     * Upload an image from bytes to R2
     */
    public String uploadImage(byte[] fileBytes, String fileName) throws IOException {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".heic") || lowerName.endsWith(".heif")) {
            // Convert HEIC bytes to JPG before uploading
            File tempHeicFile = File.createTempFile("temp-heic-", ".heic");
            File jpgFile = File.createTempFile("converted-", ".jpg");
            try {
                Files.write(tempHeicFile.toPath(), fileBytes);
                MediaConversionService.convertHeicToJpg(tempHeicFile, jpgFile);
                String jpgName = fileName.replaceAll("(?i)\\.heic$", ".jpg");
                String result = uploadFile(jpgFile, jpgName, "images/");
                tempHeicFile.delete();
                jpgFile.delete();
                return result;
            } catch (Exception e) {
                tempHeicFile.delete();
                jpgFile.delete();
                log.error("HEIC to JPG conversion failed", e);
                throw new ImageProcessingException("HEIC to JPG conversion failed", e);
            }
        }
        return uploadFile(fileBytes, fileName, "images/", detectContentType(fileName));
    }

    /**
     * Upload a video file to R2
     */
    public String uploadVideo(File videoFile, String fileName) throws IOException {
        return uploadFile(videoFile, fileName, "videos/");
    }

    /**
     * Upload a video from bytes to R2
     */
    public String uploadVideo(byte[] fileBytes, String fileName) throws IOException {
        return uploadFile(fileBytes, fileName, "videos/", detectContentType(fileName));
    }

    /**
     * Upload file from File object
     * Returns the object key (not a URL) since we'll generate presigned URLs on demand
     */
    private String uploadFile(File file, String fileName, String folder) throws IOException {
        String key = folder + fileName;
        String contentType = Files.probeContentType(file.toPath());
        
        if (contentType == null) {
            contentType = detectContentType(fileName);
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
            log.info("Successfully uploaded file to R2: {}", key);
            // Return the object key instead of a URL - URLs will be generated on demand
            return key;
        } catch (Exception e) {
            log.error("Failed to upload file to R2: {}", key, e);
            throw e;
        }
    }

    /**
     * Upload file from byte array
     * Returns the object key (not a URL) since we'll generate presigned URLs on demand
     */
    private String uploadFile(byte[] fileBytes, String fileName, String folder, String contentType) throws IOException {
        String key = folder + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
            log.info("Successfully uploaded file to R2: {}", key);
            // Return the object key instead of a URL - URLs will be generated on demand
            return key;
        } catch (Exception e) {
            log.error("Failed to upload file to R2: {}", key, e);
            throw e;
        }
    }

    /**
     * Generate a secure time-limited URL for accessing content using S3 presigner.
     * @param objectKey The object key (without bucket name)
     * @param isApproved Whether the content is approved
     * @param isAdmin Whether the requester is an admin (admins can view unapproved)
     */
    public String getSecureUrl(String objectKey, boolean isApproved, boolean isAdmin) {
        if (objectKey == null) {
            throw new IllegalArgumentException("Object key cannot be null");
        }
        if (!isApproved && !isAdmin) {
            throw new SecurityException("Access denied: Content not approved for public viewing");
        }
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(urlExpirationMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();
            String presignedUrl = presigner.presignGetObject(presignRequest).url().toString();
            
            // Replace the CloudFlare endpoint with our custom domain
            String customUrl = replaceEndpointWithCustomDomain(presignedUrl);
            
            if (log.isDebugEnabled()) {
                log.debug("Generated presigned URL (adminAccess={}): key='{}' -> '{}'", isAdmin, objectKey, customUrl.split("\\?",2)[0]);
            }
            return customUrl;
        } catch (Exception e) {
            log.error("Failed to presign GET URL for {}: {}", objectKey, e.getMessage());
            throw new RuntimeException("Failed to generate secure URL", e);
        }
    }

    /**
     * Generate a secure URL for thumbnails.
     * @param thumbnailKey The object key of the thumbnail (e.g., "thumbnails/uuid")
     * @param isApproved Whether the content is approved
     * @param isAdmin Whether the requester is an admin
     */
    public String getSecureThumbnailUrl(String thumbnailKey, boolean isApproved, boolean isAdmin) {
        if (thumbnailKey == null || thumbnailKey.isBlank()) {
            return null;
        }
        return getSecureUrl(thumbnailKey, isApproved, isAdmin);
    }

    /**
     * Generate a presigned PUT URL for direct uploads (optional helper if needed later).
     */
    public String generatePresignedUploadUrl(String objectKey, String contentType, String sha256hash, long contentLength) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    // .checksumSHA256(sha256hash)
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(urlExpirationMinutes))
                    .putObjectRequest(putObjectRequest)
                    .build();
            String presignedUrl = presigner.presignPutObject(presignRequest).url().toString();
            
            // For upload URLs, we must return the original R2 endpoint, not the custom domain
            // Custom domains only work for downloads, not authenticated uploads
            return presignedUrl;
        } catch (Exception e) {
            log.error("Failed to presign PUT URL for {}: {}", objectKey, e.getMessage());
            throw new RuntimeException("Failed to generate upload URL", e);
        }
    }

    /**
     * Delete an object from R2
     */
    public void deleteObject(String objectKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted object from R2: {}", objectKey);
        } catch (Exception e) {
            log.error("Failed to delete object from R2: {}", objectKey, e);
            throw new RuntimeException("Failed to delete object from R2", e);
        }
    }

    /**
     * Extract object key from a full URL or return as-is if already a key.
     * Handles both path-style (endpoint/bucket/key) and virtual-host styles.
     */
    public String extractObjectKeyFromUrl(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isBlank()) {
            return null;
        }
        // If not an URL assume it's an object key already
        if (!urlOrKey.startsWith("http")) {
            return urlOrKey;
        }
        try {
            URI uri = URI.create(urlOrKey);
            String path = uri.getPath(); // e.g. /bucketName/folder/file or /folder/file (if virtual host)
            if (path == null || path.isBlank()) {
                return null;
            }
            // Remove leading slash
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            // If path starts with bucketName/, strip it
            if (path.startsWith(bucketName + "/")) {
                path = path.substring(bucketName.length() + 1);
            }
            return path;
        } catch (Exception e) {
            log.error("Error extracting object key from URL: {}", urlOrKey, e);
            return null;
        }
    }

    /**
     * TEMP retained methods for compatibility; now return presigned URLs.
     */
    public String generateImageThumbnail(String objectKey) {
        return getSecureUrl(objectKey, true, true);
    }

    public String generateVideoThumbnail(String objectKey) {
        return getSecureUrl(objectKey, true, true);
    }

    /**
     * Detect content type based on file extension
     */
    private String detectContentType(String fileName) {
        String lowerCaseFileName = fileName.toLowerCase();
        
        // Images
        if (lowerCaseFileName.endsWith(".jpg") || lowerCaseFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerCaseFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerCaseFileName.endsWith(".heic")) {
            return "image/heic";
        }
        
        // Videos
        else if (lowerCaseFileName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerCaseFileName.endsWith(".mov")) {
            return "video/quicktime";
        } else if (lowerCaseFileName.endsWith(".mkv")) {
            return "video/x-matroska";
        } else if (lowerCaseFileName.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        
        return "application/octet-stream";
    }

    /**
     * Check if object exists in R2
     */
    public boolean objectExists(String objectKey) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            
            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking if object exists: {}", objectKey, e);
            return false;
        }
    }
}
