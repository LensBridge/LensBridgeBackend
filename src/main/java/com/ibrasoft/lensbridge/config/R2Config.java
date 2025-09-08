package com.ibrasoft.lensbridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Configuration class for Cloudflare R2 storage.
 * Provides S3Client bean for direct R2 operations.
 */
@Configuration
public class R2Config {
    
    @Value("${cloudflare.r2.access-key-id}")
    private String accessKeyId;

    @Value("${cloudflare.r2.secret-access-key}")
    private String secretAccessKey;

    @Value("${cloudflare.r2.endpoint}")
    private String endpoint;

    /**
     * Provides S3Client bean for Cloudflare R2 operations.
     * This is used by controllers that need direct access to R2 metadata operations.
     */
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true) // Cloudflare R2 requires path-style for the root endpoint
                .build();
                
        return S3Client.builder()
                .endpointOverride(URI.create(normalizedEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .region(Region.US_EAST_1)
                .build();
    }
    
    private String normalizeEndpoint(String ep) {
        if (ep == null) return null;
        String trimmed = ep.trim();
        // Remove any trailing slash
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
