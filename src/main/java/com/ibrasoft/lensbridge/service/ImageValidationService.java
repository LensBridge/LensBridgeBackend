package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.ImageProcessingProperties;
import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.exception.ImageDimensionsExceededException;
import com.ibrasoft.lensbridge.exception.InvalidContentTypeException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side inspection of image bytes.
 * <p>
 * Nothing upstream of this class looks at what was actually uploaded: the presign step
 * validates a client-supplied {@code contentType} string, and the completion step only
 * compares byte counts and a client-supplied SHA-256 against the stored object. Both pass
 * happily for an object that is not an image at all, or that is an image declaring a
 * resolution large enough to exhaust the heap the moment ImageIO decodes it.
 * <p>
 * Two checks close that gap, and both must run <em>before</em> any decode:
 * <ol>
 *   <li>{@link #validateContainerType} sniffs the leading bytes and requires the real
 *       container to be on the upload allowlist and to match what the client declared.</li>
 *   <li>{@link #validateDimensions} reads only the image header through
 *       {@link ImageReader#getWidth(int)} / {@link ImageReader#getHeight(int)} and rejects
 *       anything above {@code image-processing.max-megapixels}. A decompression bomb is
 *       tiny on the wire and enormous in memory, so byte-length limits never catch it.</li>
 * </ol>
 * Deliberately implemented against the JDK's own ImageIO readers plus a small magic-byte
 * table rather than pulling in a content-detection dependency; the allowlist is five
 * formats long and their signatures are fixed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageValidationService {

    /** Bytes needed to identify the longest signature we look for (ISO-BMFF brand at 8..12). */
    private static final int SIGNATURE_WINDOW = 12;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87A_MAGIC = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89A_MAGIC = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RIFF_MAGIC = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_FORM = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FTYP_BOX = "ftyp".getBytes(StandardCharsets.US_ASCII);

    /**
     * ISO-BMFF brands that mean "this file holds HEIF/HEIC image data". {@code mif1}/{@code msf1}
     * are the generic HEIF brands Apple and Android both emit alongside the {@code heic} family.
     */
    private static final Set<String> HEIC_BRANDS = Set.of(
            "heic", "heix", "heim", "heis",
            "hevc", "hevx", "hevm", "hevs",
            "mif1", "msf1");

    private final UploadProperties uploadProperties;
    private final ImageProcessingProperties imageProcessingProperties;

    /**
     * Runs both checks in the order that keeps untrusted bytes away from a decoder: identify
     * the container first, then read the header for its declared size.
     *
     * @param bytes                 the object as actually stored
     * @param declaredContentType   the content type the client claimed, possibly with parameters
     * @return the sniffed content type
     */
    public String validateImageBytes(byte[] bytes, String declaredContentType) {
        String sniffed = validateContainerType(bytes, declaredContentType);
        validateDimensions(bytes);
        return sniffed;
    }

    /**
     * Confirms the real container type is on {@code uploads.allowed-file-types} and is the one
     * the client said it was uploading.
     *
     * @return the sniffed content type
     * @throws InvalidContentTypeException if the bytes are unrecognised, not allowlisted, or
     *                                     disagree with {@code declaredContentType}
     */
    public String validateContainerType(byte[] bytes, String declaredContentType) {
        String sniffed = detectContentType(bytes);
        if (sniffed == null) {
            log.warn("Rejected upload: bytes match no supported image signature (declared '{}')",
                    declaredContentType);
            throw new InvalidContentTypeException(declaredContentType,
                    "File is not a recognized image; its contents match no supported format");
        }

        List<String> allowed = uploadProperties.getAllowedFileTypes();
        if (allowed == null || !allowed.contains(sniffed)) {
            throw new InvalidContentTypeException(sniffed);
        }

        String declared = normalizeContentType(declaredContentType);
        if (!sniffed.equals(declared)) {
            log.warn("Rejected upload: declared content type '{}' but bytes are {}",
                    declaredContentType, sniffed);
            // The message names only the declared type; echoing the sniffed one back would tell
            // a prober exactly which signatures the server recognises.
            throw new InvalidContentTypeException(declaredContentType,
                    "File contents do not match the declared content type '"
                            + declaredContentType + "'");
        }

        return sniffed;
    }

    /**
     * Rejects images whose header declares more pixels than {@code image-processing.max-megapixels}.
     * <p>
     * Only the header is parsed — {@link ImageReader#getWidth(int)} on a PNG reads IHDR and stops,
     * so a bomb is rejected without ever allocating its raster. Formats the JDK has no reader for
     * (HEIC, WebP) are passed through: nothing downstream can decode them either, so they carry no
     * decompression risk from ImageIO.
     *
     * @throws ImageDimensionsExceededException if the declared resolution is over the ceiling
     */
    public void validateDimensions(byte[] bytes) {
        long maxPixels = maxPixels();

        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                log.debug("No ImageIO reader for uploaded bytes; skipping dimension check");
                return;
            }

            ImageReader reader = readers.next();
            try {
                // ignoreMetadata=true keeps the reader from walking ancillary chunks.
                reader.setInput(input, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                long pixels = width * height;

                if (pixels > maxPixels) {
                    log.warn("Rejected upload: {}x{} = {} pixels exceeds the {} pixel ceiling",
                            width, height, pixels, maxPixels);
                    throw new ImageDimensionsExceededException(width, height, maxPixels);
                }

                log.debug("Image dimensions accepted: {}x{} ({} pixels)", width, height, pixels);
            } finally {
                reader.dispose();
            }
        } catch (ImageDimensionsExceededException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // A reader claimed the format, then could not parse its header. That is a file
            // pretending to be something it is not, and we cannot vouch for what a decoder
            // would do with it.
            log.warn("Could not read image dimensions: {}", e.getMessage());
            throw new InvalidContentTypeException(null,
                    "Image header could not be read; the file may be corrupt or truncated");
        }
    }

    /**
     * @return the configured ceiling in pixels
     */
    public long maxPixels() {
        return (long) imageProcessingProperties.getMaxMegapixels() * 1_000_000L;
    }

    /**
     * Identifies the container from its leading bytes.
     *
     * @return the canonical content type, or {@code null} if the signature is not one we accept
     */
    public String detectContentType(byte[] bytes) {
        if (bytes == null || bytes.length < SIGNATURE_WINDOW) {
            return null;
        }
        if (startsWith(bytes, JPEG_MAGIC, 0)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, PNG_MAGIC, 0)) {
            return "image/png";
        }
        if (startsWith(bytes, GIF87A_MAGIC, 0) || startsWith(bytes, GIF89A_MAGIC, 0)) {
            return "image/gif";
        }
        // RIFF....WEBP — the four bytes between the two tags are the chunk length.
        if (startsWith(bytes, RIFF_MAGIC, 0) && startsWith(bytes, WEBP_FORM, 8)) {
            return "image/webp";
        }
        // ISO-BMFF: a 4-byte box length, then "ftyp", then the major brand.
        if (startsWith(bytes, FTYP_BOX, 4)) {
            String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII)
                    .toLowerCase(Locale.ROOT);
            if (HEIC_BRANDS.contains(brand)) {
                return "image/heic";
            }
        }
        return null;
    }

    /** Strips media-type parameters and case so {@code image/JPEG; charset=x} compares equal. */
    public static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] bytes, byte[] signature, int offset) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        return Arrays.equals(bytes, offset, offset + signature.length, signature, 0, signature.length);
    }
}
