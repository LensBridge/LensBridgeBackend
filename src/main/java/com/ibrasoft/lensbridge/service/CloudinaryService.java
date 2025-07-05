package com.ibrasoft.lensbridge.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public String uploadImage(byte[] fileBytes, String fileName) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                "folder", "lensbridge",
                "public_id", fileName
        ));

        return (String) uploadResult.get("secure_url");
    }

    public String uploadVideo(byte[] fileBytes, String fileName) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                "folder", "lensbridge",
                "resource_type", "video",
                "public_id", fileName
        ));
        return (String) uploadResult.get("secure_url");
    }

    public String uploadVideo(File transcodedFile, String fileName) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(transcodedFile, ObjectUtils.asMap(
                "folder", "lensbridge",
                "resource_type", "video",
                "public_id", fileName
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
            // Extract the public ID from the URL
            // Example: https://res.cloudinary.com/cloud/image/upload/v1234567890/lensbridge/filename.jpg
            // or: https://res.cloudinary.com/cloud/video/upload/v1234567890/lensbridge/filename.mp4
            
            // Find the part after "/upload/"
            String uploadPattern = "/upload/";
            int uploadIndex = url.indexOf(uploadPattern);
            if (uploadIndex == -1) {
                return null;
            }
            
            String afterUpload = url.substring(uploadIndex + uploadPattern.length());
            
            // Skip version if present (starts with v followed by numbers)
            if (afterUpload.startsWith("v") && afterUpload.length() > 1) {
                int nextSlash = afterUpload.indexOf("/");
                if (nextSlash != -1) {
                    afterUpload = afterUpload.substring(nextSlash + 1);
                }
            }
            
            // Remove file extension
            int lastDot = afterUpload.lastIndexOf('.');
            if (lastDot != -1) {
                afterUpload = afterUpload.substring(0, lastDot);
            }
            
            return afterUpload;
        } catch (Exception e) {
            System.err.println("Error extracting public ID from URL: " + url);
            return null;
        }
    }
}
