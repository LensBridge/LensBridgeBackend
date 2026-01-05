package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.upload.UploadType;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-time backfill script to generate thumbnails for existing uploads.
 * 
 * To run this backfill, add the following to application.properties:
 *   thumbnail.backfill.enabled=true
 * 
 * Then start the application. After completion, remove the property or set to false.
 * The application will continue running normally after backfill completes.
 */
@Component
@ConditionalOnProperty(name = "thumbnail.backfill.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ThumbnailBackfillService implements CommandLineRunner {

    private final UploadRepository uploadRepository;
    private final ThumbnailService thumbnailService;

    @Override
    public void run(String... args) {
        log.info("=".repeat(60));
        log.info("THUMBNAIL BACKFILL: Starting thumbnail generation for existing uploads");
        log.info("=".repeat(60));

        // Find all image uploads without thumbnails
        List<Upload> uploadsWithoutThumbnails = uploadRepository.findAll().stream()
                .filter(u -> u.getContentType() == UploadType.IMAGE)
                .filter(u -> u.getThumbnailUrl() == null || u.getThumbnailUrl().isBlank())
                .filter(u -> u.getFileUrl() != null && !u.getFileUrl().isBlank())
                .toList();

        int total = uploadsWithoutThumbnails.size();
        log.info("Found {} image uploads without thumbnails", total);

        if (total == 0) {
            log.info("No uploads need thumbnail backfill. Exiting backfill.");
            return;
        }

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (int i = 0; i < uploadsWithoutThumbnails.size(); i++) {
            Upload upload = uploadsWithoutThumbnails.get(i);
            int current = i + 1;

            try {
                log.info("[{}/{}] Processing upload: {} (key: {})", 
                        current, total, upload.getUuid(), upload.getFileUrl());

                // Generate thumbnail synchronously (blocking)
                String thumbnailKey = thumbnailService.generateThumbnail(upload.getFileUrl());

                // Update the upload record
                upload.setThumbnailUrl(thumbnailKey);
                uploadRepository.save(upload);

                success.incrementAndGet();
                log.info("[{}/{}] SUCCESS: Generated thumbnail: {}", current, total, thumbnailKey);

            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("[{}/{}] FAILED: Upload {} - {}", 
                        current, total, upload.getUuid(), e.getMessage());
            }

            // Progress update every 10 items
            if (current % 10 == 0) {
                log.info("Progress: {}/{} processed ({} success, {} failed)", 
                        current, total, success.get(), failed.get());
            }
        }

        log.info("=".repeat(60));
        log.info("THUMBNAIL BACKFILL COMPLETE");
        log.info("Total processed: {}", total);
        log.info("Successful: {}", success.get());
        log.info("Failed: {}", failed.get());
        log.info("=".repeat(60));
        log.info("You can now set 'thumbnail.backfill.enabled=false' and restart.");
    }
}
