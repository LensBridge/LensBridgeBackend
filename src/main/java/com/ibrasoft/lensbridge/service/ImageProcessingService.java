package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.ImageProcessingProperties;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageProcessingService {

    private final R2StorageService r2StorageService;
    private final UploadRepository uploadRepository;
    private final ImageProcessingProperties properties;

    @Async
    public CompletableFuture<String> generateThumbnail(Upload upload) {
        try {
            String objectKey = upload.getFileUrl();
            log.info("Generating thumbnail for upload {} (key: {})", upload.getUuid(), objectKey);

            byte[] originalBytes = r2StorageService.getObjectBytes(objectKey);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(originalBytes))
                    .size(properties.getThumbnailWidth(), properties.getThumbnailHeight())
                    .keepAspectRatio(true)
                    .outputQuality(properties.getThumbnailQuality())
                    .outputFormat("jpg")
                    .toOutputStream(out);

            byte[] thumbnailBytes = out.toByteArray();
            String thumbnailKey = resolveThumbnailKey(objectKey);

            r2StorageService.putObject(
                    thumbnailKey,
                    new ByteArrayInputStream(thumbnailBytes),
                    thumbnailBytes.length,
                    "image/jpeg");

            Optional<Upload> fresh = uploadRepository.findById(upload.getUuid());
            if (fresh.isPresent()) {
                fresh.get().setThumbnailUrl(thumbnailKey);
                uploadRepository.save(fresh.get());
            }

            log.info("Thumbnail generated: {} -> {} ({} bytes)", objectKey, thumbnailKey, thumbnailBytes.length);
            return CompletableFuture.completedFuture(thumbnailKey);

        } catch (Exception e) {
            log.error("Failed to generate thumbnail for upload {}: {}", upload.getUuid(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private String resolveThumbnailKey(String objectKey) {
        String filename = objectKey.contains("/")
                ? objectKey.substring(objectKey.lastIndexOf('/') + 1)
                : objectKey;
        return properties.getThumbnailFolder() + filename;
    }
}
