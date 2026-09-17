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
}
