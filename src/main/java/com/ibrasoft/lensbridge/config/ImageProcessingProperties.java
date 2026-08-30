package com.ibrasoft.lensbridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "image-processing")
@Data
public class ImageProcessingProperties {
    private int thumbnailWidth;
    private int thumbnailHeight;
    private double thumbnailQuality;
    private String thumbnailFolder;

    /**
     * Ceiling on the resolution the server will decode, in megapixels.
     * <p>
     * The other fields here bound the thumbnail we produce; this one bounds the input. A
     * decompression bomb is a few kilobytes on the wire that declares a resolution large enough
     * to exhaust the heap when ImageIO expands it — every byte-count limit in the upload path
     * lets it straight through. 50 MP sits well above any consumer camera (a 100 MP phone
     * sensor is the only realistic exception) while capping a single decode at roughly 200 MB
     * of ARGB raster.
     */
    private int maxMegapixels = 50;
}
