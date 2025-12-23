package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for generating and managing image thumbnails.
 * Thumbnails are generated asynchronously after image uploads complete.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbnailService {

    private final S3Client s3Client;
    private final UploadRepository uploadRepository;

    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    @Value("${thumbnail.width:400}")
    private int thumbnailWidth;

    @Value("${thumbnail.height:400}")
    private int thumbnailHeight;

    @Value("${thumbnail.quality:0.85}")
    private double thumbnailQuality;

    @Value("${thumbnail.folder:thumbnails/}")
    private String thumbnailFolder;

    /**
     * Generate a thumbnail for an uploaded image asynchronously.
     * The thumbnail is stored in the thumbnails/ folder with the same filename.
     * Updates the Upload entity with the thumbnail URL after generation.
     *
     * @param uploadId  The UUID of the upload to generate thumbnail for
     * @param objectKey The R2 object key of the original image (e.g., "images/uuid")
     */
    @Async
    public void generateThumbnailAsync(UUID uploadId, String objectKey) {
        try {
            log.info("Starting thumbnail generation for upload {} (key: {})", uploadId, objectKey);
            String thumbnailKey = generateThumbnail(objectKey);
            
            // Update the upload entity with the thumbnail URL
            Optional<Upload> uploadOpt = uploadRepository.findById(uploadId);
            if (uploadOpt.isPresent()) {
                Upload upload = uploadOpt.get();
                upload.setThumbnailUrl(thumbnailKey);
                uploadRepository.save(upload);
                log.info("Successfully updated upload {} with thumbnail: {}", uploadId, thumbnailKey);
            } else {
                log.warn("Upload {} not found when trying to update thumbnail URL", uploadId);
            }
        } catch (Exception e) {
            log.error("Failed to generate thumbnail for upload {} (key: {}): {}", 
                uploadId, objectKey, e.getMessage(), e);
        }
    }

    /**
     * Generate a thumbnail for an image stored in R2.
     *
     * @param objectKey The R2 object key of the original image
     * @return The R2 object key of the generated thumbnail
     */
    public String generateThumbnail(String objectKey) throws Exception {
        // Download the original image from R2
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        byte[] thumbnailBytes;
        try (ResponseInputStream<GetObjectResponse> objectStream = s3Client.getObject(getRequest)) {
            // Generate thumbnail using Thumbnailator
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            Thumbnails.of(objectStream)
                    .size(thumbnailWidth, thumbnailHeight)
                    .keepAspectRatio(true)
                    .outputQuality(thumbnailQuality)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);
            
            thumbnailBytes = outputStream.toByteArray();
        }

        // Generate thumbnail key: thumbnails/<original-filename>
        // e.g., images/uuid -> thumbnails/uuid
        String filename = extractFilename(objectKey);
        String thumbnailKey = thumbnailFolder + filename;

        // Upload thumbnail to R2
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(thumbnailKey)
                .contentType("image/jpeg")
                .contentLength((long) thumbnailBytes.length)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(thumbnailBytes));
        
        log.info("Generated thumbnail: {} -> {} ({}x{}, {} bytes)", 
                objectKey, thumbnailKey, thumbnailWidth, thumbnailHeight, thumbnailBytes.length);
        
        return thumbnailKey;
    }

    /**
     * Extract the filename from an object key.
     * e.g., "images/abc-123" -> "abc-123"
     */
    private String extractFilename(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
    }

    /**
     * Check if a thumbnail exists for the given object key.
     */
    public boolean thumbnailExists(String objectKey) {
        String filename = extractFilename(objectKey);
        String thumbnailKey = thumbnailFolder + filename;
        
        try {
            s3Client.headObject(builder -> builder.bucket(bucketName).key(thumbnailKey));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the thumbnail key for a given original object key.
     */
    public String getThumbnailKey(String objectKey) {
        String filename = extractFilename(objectKey);
        return thumbnailFolder + filename;
    }
}
