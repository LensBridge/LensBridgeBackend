package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.ImageProcessingProperties;
import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.exception.ImageDimensionsExceededException;
import com.ibrasoft.lensbridge.exception.InvalidContentTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Byte-level validation of uploaded images.
 *
 * <p>Before this existed, the only checks on an uploaded object were its declared
 * {@code Content-Type} (a client-supplied string) and its byte length against R2's stored
 * {@code Content-Length}. Nothing looked at the bytes. So a file could claim {@code image/png},
 * be a ZIP, and reach {@code Thumbnails.of(...)} — and a PNG declaring an enormous resolution in
 * its header could be a few hundred bytes on the wire and still ask ImageIO to allocate a
 * multi-gigabyte raster on the async thumbnail pool.
 *
 * <p>The decompression bomb below is the real thing: a valid PNG header advertising
 * 40000x40000 (1.6 gigapixels) whose image data is never decoded, because the dimension check
 * runs off the header before {@code ImageIO.read} is ever called.
 */
class ImageValidationServiceTest {

    private ImageValidationService service;

    @BeforeEach
    void setUp() {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setAllowedFileTypes(
                List.of("image/jpeg", "image/png", "image/heic", "image/webp", "image/gif"));
        ImageProcessingProperties imageProcessingProperties = new ImageProcessingProperties();
        imageProcessingProperties.setMaxMegapixels(50);
        service = new ImageValidationService(uploadProperties, imageProcessingProperties);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static byte[] realPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * A PNG whose IHDR advertises {@code width x height} but which carries no pixel data. Small
     * on the wire, ruinous to decode — the classic decompression bomb shape.
     */
    private static byte[] pngClaimingDimensions(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        ihdr.writeBytes("IHDR".getBytes(StandardCharsets.US_ASCII));
        ihdr.writeBytes(intBytes(width));
        ihdr.writeBytes(intBytes(height));
        ihdr.write(8);      // bit depth
        ihdr.write(2);      // colour type: truecolour
        ihdr.write(0);      // compression
        ihdr.write(0);      // filter
        ihdr.write(0);      // interlace
        byte[] chunk = ihdr.toByteArray();
        out.writeBytes(intBytes(chunk.length - 4)); // length excludes the type field
        out.writeBytes(chunk);
        CRC32 crc = new CRC32();
        crc.update(chunk);
        out.writeBytes(intBytes((int) crc.getValue()));
        return out.toByteArray();
    }

    private static byte[] intBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    /** Local file header of a ZIP archive. Not an image by any reading of the bytes. */
    private static byte[] zipBytes() {
        byte[] bytes = new byte[64];
        bytes[0] = 'P';
        bytes[1] = 'K';
        bytes[2] = 0x03;
        bytes[3] = 0x04;
        return bytes;
    }

    // ── container type ────────────────────────────────────────────────────────

    @Test
    void acceptsAGenuinePngDeclaredAsPng() throws IOException {
        assertThat(service.validateImageBytes(realPng(8, 8), "image/png")).isEqualTo("image/png");
    }

    @Test
    void rejectsAZipDeclaredAsPng() {
        assertThatThrownBy(() -> service.validateImageBytes(zipBytes(), "image/png"))
                .isInstanceOf(InvalidContentTypeException.class);
    }

    @Test
    void rejectsAGenuinePngDeclaredAsJpeg() throws IOException {
        // The declared type is on the allowlist and the bytes are a real image; they simply are
        // not the same thing. Trusting the declaration is what let non-images through before.
        assertThatThrownBy(() -> service.validateImageBytes(realPng(8, 8), "image/jpeg"))
                .isInstanceOf(InvalidContentTypeException.class);
    }

    @Test
    void rejectionMessageDoesNotEchoTheSniffedType() throws IOException {
        // Naming the detected format would tell a prober exactly which signatures are recognised.
        assertThatThrownBy(() -> service.validateImageBytes(realPng(8, 8), "image/jpeg"))
                .hasMessageContaining("image/jpeg")
                .hasMessageNotContaining("image/png");
    }

    @Test
    void rejectsEmptyAndTruncatedInput() {
        assertThatThrownBy(() -> service.validateImageBytes(new byte[0], "image/png"))
                .isInstanceOf(InvalidContentTypeException.class);
        assertThatThrownBy(() -> service.validateImageBytes(new byte[]{(byte) 0x89, 'P'}, "image/png"))
                .isInstanceOf(InvalidContentTypeException.class);
    }

    // ── dimensions ────────────────────────────────────────────────────────────

    @Test
    void rejectsADecompressionBombBeforeDecodingIt() {
        // 40000x40000 = 1.6 gigapixels, from a payload of well under a kilobyte.
        byte[] bomb = pngClaimingDimensions(40_000, 40_000);
        assertThat(bomb.length).isLessThan(1024);

        assertThatThrownBy(() -> service.validateDimensions(bomb))
                .isInstanceOf(ImageDimensionsExceededException.class);
    }

    @Test
    void acceptsAnImageUnderThePixelCeiling() {
        assertThatCode(() -> service.validateDimensions(pngClaimingDimensions(4_000, 4_000)))
                .doesNotThrowAnyException();
    }

    @Test
    void pixelCeilingIsConfigurable() {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setAllowedFileTypes(List.of("image/png"));
        ImageProcessingProperties strict = new ImageProcessingProperties();
        strict.setMaxMegapixels(1);
        ImageValidationService strictService = new ImageValidationService(uploadProperties, strict);

        // 2000x2000 = 4 MP: fine at the default 50 MP, rejected at 1 MP.
        byte[] bytes = pngClaimingDimensions(2_000, 2_000);
        assertThatCode(() -> service.validateDimensions(bytes)).doesNotThrowAnyException();
        assertThatThrownBy(() -> strictService.validateDimensions(bytes))
                .isInstanceOf(ImageDimensionsExceededException.class);
    }
}
