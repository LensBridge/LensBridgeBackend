package com.ibrasoft.lensbridge.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Cloudflare R2 storage.
 * R2 configuration is handled by the R2StorageService using @Value annotations
 * and application.properties.
 */
@Configuration
public class R2Config {
    
    // R2 configuration is managed by R2StorageService
    // using @Value annotations to inject properties from application.properties
    
}
