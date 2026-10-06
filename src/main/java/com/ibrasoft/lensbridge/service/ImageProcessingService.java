package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.ImageProcessingProperties;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
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

            long pixels = readPixelCount(originalBytes);
            if (pixels > properties.getMaxSourcePixels()) {
                log.warn("Skipping thumbnail for upload {}: {} pixels exceeds the {} pixel cap",
                        upload.getUuid(), pixels, properties.getMaxSourcePixels());
                return CompletableFuture.completedFuture(null);
            }

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

    /**
     * Pixel count from the image header alone, so a decompression bomb is rejected before any
     * pixel buffer is allocated. -1 when no reader recognises the format (HEIC, say); that
     * case is left to the thumbnailer exactly as before. The in-memory stream avoids the temp
     * file ImageIO's default stream cache would create.
     */
    private long readPixelCount(byte[] bytes) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return -1;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return (long) reader.getWidth(0) * reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private String resolveThumbnailKey(String objectKey) {
        String filename = objectKey.contains("/")
                ? objectKey.substring(objectKey.lastIndexOf('/') + 1)
                : objectKey;
        return properties.getThumbnailFolder() + filename;
    }
}
