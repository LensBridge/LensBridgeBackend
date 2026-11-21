package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.ImageProcessingProperties;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageProcessingServiceTest {

    private final R2StorageService r2 = mock(R2StorageService.class);
    private final UploadRepository uploadRepository = mock(UploadRepository.class);
    private final ImageProcessingProperties properties = new ImageProcessingProperties();
    private ImageProcessingService service;
    private Upload upload;

    @BeforeEach
    void setUp() {
        properties.setThumbnailWidth(10);
        properties.setThumbnailHeight(10);
        properties.setThumbnailQuality(0.7);
        properties.setThumbnailFolder("thumbnails/");
        service = new ImageProcessingService(r2, uploadRepository, properties);

        upload = new Upload();
        upload.setUuid(UUID.randomUUID());
        upload.setFileUrl("images/abc");
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void anImageWithinTheCapGetsAThumbnail() throws Exception {
        when(r2.getObjectBytes("images/abc")).thenReturn(png(40, 30));
        when(uploadRepository.findById(upload.getUuid())).thenReturn(Optional.of(upload));

        String key = service.generateThumbnail(upload).get();

        assertThat(key).isEqualTo("thumbnails/abc");
        verify(r2).putObject(eq("thumbnails/abc"), any(InputStream.class), anyLong(), eq("image/jpeg"));
        assertThat(upload.getThumbnailUrl()).isEqualTo("thumbnails/abc");
    }

    @Test
    void anImageAboveThePixelCapIsSkippedWithoutBeingDecoded() throws Exception {
        properties.setMaxSourcePixels(1_000);
        when(r2.getObjectBytes("images/abc")).thenReturn(png(40, 30)); // 1200 pixels

        String key = service.generateThumbnail(upload).get();

        assertThat(key).isNull();
        verify(r2, never()).putObject(anyString(), any(), anyLong(), anyString());
        verify(uploadRepository, never()).save(any());
    }

    @Test
    void theCapIsInclusive() throws Exception {
        properties.setMaxSourcePixels(1_200);
        when(r2.getObjectBytes("images/abc")).thenReturn(png(40, 30));
        when(uploadRepository.findById(upload.getUuid())).thenReturn(Optional.of(upload));

        assertThat(service.generateThumbnail(upload).get()).isEqualTo("thumbnails/abc");
    }

    @Test
    void theDefaultCapIsFiftyMegapixels() {
        assertThat(new ImageProcessingProperties().getMaxSourcePixels()).isEqualTo(50_000_000L);
    }

    @Test
    void aFormatNoReaderRecognisesIsLeftToTheThumbnailerAsBefore() throws Exception {
        when(r2.getObjectBytes("images/abc")).thenReturn(new byte[] { 1, 2, 3, 4 });

        // Thumbnailator cannot decode it either, which fails the future exactly as it always did
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateThumbnail(upload).get())
                .hasCauseInstanceOf(Exception.class);
        verify(r2, never()).putObject(anyString(), any(), anyLong(), anyString());
    }
}
