package com.ibrasoft.lensbridge.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;
    
    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    public String uploadImage(byte[] fileBytes, String fileName) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                "folder", "lensbridge",
                "public_id", fileName,
                "type", "private",  // Private upload - requires signed URLs for access
                "format", "png"     // Force conversion to PNG
        ));

        return (String) uploadResult.get("secure_url");
    }

    public String uploadImage(File imageFile, String fileName) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(imageFile, ObjectUtils.asMap(
                "folder", "lensbridge",
                "public_id", fileName,
                "type", "private",  // Private upload - requires signed URLs for access
                "format", "png"     // Force conversion to PNG
        ));
        return (String) uploadResult.get("secure_url");
    }

    public String uploadVideo(byte[] fileBytes, String fileName) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                "folder", "lensbridge",
                "resource_type", "video",
                "public_id", fileName,
                "type", "private"  // Private upload - requires signed URLs for access
        ));
        return (String) uploadResult.get("secure_url");
    }

    public String uploadVideo(File transcodedFile, String fileName) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(transcodedFile, ObjectUtils.asMap(
                "folder", "lensbridge",
                "resource_type", "video",
                "public_id", fileName,
                "type", "private"  // Private upload - requires signed URLs for access
        ));
        return (String) uploadResult.get("secure_url");
    }

    /**
     * Generate thumbnail URL for images
     */
    public String generateImageThumbnail(String publicId) {
        return cloudinary.url()
                .resourceType("image")
                .publicId(publicId)
                .generate() + "?w=300&h=300&c=fill&q=auto";
    }

    /**
     * Generate thumbnail URL for videos
     */
    public String generateVideoThumbnail(String publicId) {
        return cloudinary.url()
                .resourceType("video")
                .publicId(publicId)
                .generate() + "?w=300&h=300&c=fill&q=auto&f=jpg";
    }

    /**
     * Extract public ID from Cloudinary URL
     */
    public String extractPublicIdFromUrl(String url) {
        if (url == null || !url.contains("cloudinary.com")) {
            return null;
        }
        
        try {
            // Handle different URL patterns:
            // 1. Standard upload: https://res.cloudinary.com/cloud/image/upload/v1234567890/lensbridge/filename.jpg
            // 2. Private signed: https://res.cloudinary.com/cloud/image/private/s--signature--/v1234567890/lensbridge/filename.jpg
            
            String afterCloudinary = url;
            
            // Check if it's a signed private URL first
            if (url.contains("/private/s--")) {
                // For signed private URLs: /private/s--signature--/v1234567890/public_id
                String privatePattern = "/private/s--";
                int privateIndex = url.indexOf(privatePattern);
                if (privateIndex != -1) {
                    // Find the end of the signature (next --)
                    int signatureStart = privateIndex + privatePattern.length();
                    int signatureEnd = url.indexOf("--/", signatureStart);
                    if (signatureEnd != -1) {
                        afterCloudinary = url.substring(signatureEnd + 3); // Skip "--/"
                    }
                }
            } else {
                // For standard URLs: /upload/v1234567890/public_id
                String uploadPattern = "/upload/";
                int uploadIndex = url.indexOf(uploadPattern);
                if (uploadIndex == -1) {
                    return null;
                }
                afterCloudinary = url.substring(uploadIndex + uploadPattern.length());
            }
            
            // Skip version if present (starts with v followed by numbers)
            if (afterCloudinary.startsWith("v") && afterCloudinary.length() > 1) {
                int nextSlash = afterCloudinary.indexOf("/");
                if (nextSlash != -1) {
                    afterCloudinary = afterCloudinary.substring(nextSlash + 1);
                }
            }
            
            // Remove file extension
            int lastDot = afterCloudinary.lastIndexOf('.');
            if (lastDot != -1) {
                afterCloudinary = afterCloudinary.substring(0, lastDot);
            }
            
            return afterCloudinary;
        } catch (Exception e) {
            log.error("Error extracting public ID from URL: {}", url, e);
            return null;
        }
    }

    // ============= SIGNED URL SECURITY METHODS =============

    /**
     * Generate a signed URL that works for both public and private resources
     * @param publicId The public ID of the resource
     * @param resourceType The type of resource (image/video)
     * @param expirationHours How many hours the URL should be valid
     * @param originalUrl The original URL to determine if it's public or private
     * @return Signed URL that expires after the specified time
     */
    public String generateSignedUrl(String publicId, String resourceType, int expirationHours, String originalUrl) {
        try {
            // Determine if this is a private or public upload based on the URL
            boolean isPrivateUpload = originalUrl.contains("/private/");
            
            if (isPrivateUpload) {
                // For private resources, we need to specify the type and sign the URL
                return cloudinary.url()
                        .resourceType(resourceType)
                        .type("private")  // Specify private type for private uploads
                        .signed(true)
                        .generate(publicId);
            } else {
                // For public resources (existing uploads), use standard signing
                return cloudinary.url()
                        .resourceType(resourceType)
                        .signed(true)
                        .generate(publicId);
            }
                    
        } catch (Exception e) {
            String uploadType = originalUrl.contains("/private/") ? "private" : "public";
            log.error("Error generating signed URL for {} resource publicId: {}", uploadType, publicId, e);
            throw new RuntimeException("Failed to generate signed URL", e);
        }
    }
    
    /**
     * Generate a signed URL with time-limited access for private resources (legacy method)
     * @param publicId The public ID of the resource
     * @param resourceType The type of resource (image/video)
     * @param expirationHours How many hours the URL should be valid
     * @return Signed URL that expires after the specified time
     */
    public String generateSignedUrl(String publicId, String resourceType, int expirationHours) {
        try {
            // For new private resources, we need to specify the type and sign the URL
            return cloudinary.url()
                    .resourceType(resourceType)
                    .type("private")  // Specify private type for private uploads
                    .signed(true)
                    .generate(publicId);
                    
        } catch (Exception e) {
            log.error("Error generating signed URL for private resource publicId: {}", publicId, e);
            throw new RuntimeException("Failed to generate signed URL", e);
        }
    }

    /**
     * Get secure URL based on approval status and user role
     * @param originalUrl The original Cloudinary URL
     * @param isApproved Whether the content is approved
     * @param isAdmin Whether the requesting user is an admin
     * @return Secure signed URL or throws exception if unauthorized
     */
    public String getSecureUrl(String originalUrl, boolean isApproved, boolean isAdmin) {
        // Only admins can access unapproved content
        if (!isApproved && !isAdmin) {
            throw new SecurityException("Access denied: Content not approved for public viewing");
        }
        
        String publicId = extractPublicIdFromUrl(originalUrl);
        if (publicId == null) {
            throw new IllegalArgumentException("Invalid Cloudinary URL: " + originalUrl);
        }
        
        // Determine resource type from URL
        String resourceType = originalUrl.contains("/video/") ? "video" : "image";
        
        // Different expiration times based on user role
        int expirationHours;
        if (isAdmin) {
            expirationHours = isApproved ? 168 : 2; // Approved: 7 days, Unapproved: 2 hours
        } else {
            expirationHours = 24; // Public users: 1 day for approved content only
        }
        
        return generateSignedUrl(publicId, resourceType, expirationHours, originalUrl);
    }

    /**
     * Generate secure URL specifically for admin preview (unapproved content)
     * @param originalUrl The original Cloudinary URL
     * @return 2-hour signed URL for admin preview
     */
    public String getAdminPreviewUrl(String originalUrl) {
        String publicId = extractPublicIdFromUrl(originalUrl);
        if (publicId == null) {
            throw new IllegalArgumentException("Invalid Cloudinary URL: " + originalUrl);
        }
        
        String resourceType = originalUrl.contains("/video/") ? "video" : "image";
        return generateSignedUrl(publicId, resourceType, 2, originalUrl); // 2-hour preview
    }

    /**
     * Generate secure URL for public gallery (approved content only)
     * @param originalUrl The original Cloudinary URL
     * @param isApproved Whether the content is approved (must be true)
     * @return 24-hour signed URL for public access
     */
    public String getPublicUrl(String originalUrl, boolean isApproved) {
        if (!isApproved) {
            throw new SecurityException("Cannot generate public URL for unapproved content");
        }
        
        return getSecureUrl(originalUrl, true, false);
    }

    /**
     * Generate secure thumbnail URL
     * @param originalUrl The original Cloudinary URL
     * @param isApproved Whether the content is approved
     * @param isAdmin Whether the requesting user is an admin
     * @return Signed thumbnail URL
     */
    public String getSecureThumbnailUrl(String originalUrl, boolean isApproved, boolean isAdmin) {
        if (!isApproved && !isAdmin) {
            throw new SecurityException("Access denied: Content not approved for public viewing");
        }
        
        String publicId = extractPublicIdFromUrl(originalUrl);
        if (publicId == null) {
            throw new IllegalArgumentException("Invalid Cloudinary URL: " + originalUrl);
        }
        
        String resourceType = originalUrl.contains("/video/") ? "video" : "image";
        
        try {
            // Determine if this is a private or public upload based on the URL
            boolean isPrivateUpload = originalUrl.contains("/private/");
            
            String baseUrl;
            if (isPrivateUpload) {
                // For private resources, specify the type
                baseUrl = cloudinary.url()
                        .resourceType(resourceType)
                        .type("private")
                        .signed(true)
                        .generate(publicId);
            } else {
                // For public resources (existing uploads)
                baseUrl = cloudinary.url()
                        .resourceType(resourceType)
                        .signed(true)
                        .generate(publicId);
            }
                    
            if ("video".equals(resourceType)) {
                return baseUrl + "?w=300&h=300&c=fill&q=auto&f=jpg";
            } else {
                return baseUrl + "?w=300&h=300&c=fill&q=auto";
            }
        } catch (Exception e) {
            log.error("Error generating secure thumbnail URL for publicId: {}", publicId, e);
            throw new RuntimeException("Failed to generate secure thumbnail URL", e);
        }
    }
}
