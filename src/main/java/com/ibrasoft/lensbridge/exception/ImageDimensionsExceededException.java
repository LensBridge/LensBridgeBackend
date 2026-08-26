package com.ibrasoft.lensbridge.exception;

import lombok.Getter;

/**
 * Thrown when an image's header declares more pixels than the server is willing to decode.
 * <p>
 * This is the decompression-bomb case: the file is small enough to satisfy every byte-count
 * limit, and would still allocate gigabytes the moment ImageIO expanded it into a raster.
 */
@Getter
public class ImageDimensionsExceededException extends RuntimeException {

    private final long width;
    private final long height;
    private final long maxPixels;

    public ImageDimensionsExceededException(long width, long height, long maxPixels) {
        super("Image resolution too large: " + width + "x" + height + " exceeds the "
                + (maxPixels / 1_000_000L) + " megapixel limit");
        this.width = width;
        this.height = height;
        this.maxPixels = maxPixels;
    }
}
