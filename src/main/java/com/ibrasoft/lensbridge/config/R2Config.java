package com.ibrasoft.lensbridge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Configuration class for Cloudflare R2 storage.
 * Provides S3Client and S3Presigner beans for use across the application.
 */
@Configuration
@Slf4j
public class R2Config {

    @Value("${cloudflare.r2.access-key-id}")
    private String accessKeyId;

    @Value("${cloudflare.r2.secret-access-key}")
    private String secretAccessKey;

    @Value("${cloudflare.r2.endpoint}")
    private String endpoint;

    // Spring infers close() as the destroy method of both beans, so no manual shutdown hook.
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true) // Cloudflare R2 requires path-style access
                .build();

        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(normalizedEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .region(Region.US_EAST_1)
                .build();

        log.info("S3Client initialized for R2 endpoint: {}", normalizedEndpoint);
        return client;
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(normalizedEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .region(Region.US_EAST_1)
                .build();

        log.info("S3Presigner initialized for R2 endpoint: {}", normalizedEndpoint);
        return presigner;
    }

    private String normalizeEndpoint(String ep) {
        if (ep == null) return null;
        String trimmed = ep.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
