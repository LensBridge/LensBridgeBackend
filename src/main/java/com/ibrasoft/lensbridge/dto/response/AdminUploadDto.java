package com.ibrasoft.lensbridge.dto.response;

import com.ibrasoft.lensbridge.model.upload.UploadType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUploadDto {
    private UUID uuid;
    private String fileName;
    private String fileUrl;           // Original Cloudinary URL (internal use only)
    private String secureUrl;         // Signed URL for actual access
    private String thumbnailUrl;      // Signed thumbnail URL
    private String uploadDescription;
    private String instagramHandle;
    
    // User information
    private UUID uploadedBy;
    private String uploaderFirstName;
    private String uploaderLastName;
    private String uploaderEmail;
    private String uploaderStudentNumber;
    
    // Event information
    private UUID eventId;
    private String eventName;
    
    private LocalDateTime createdDate;
    private boolean approved;
    private boolean featured;
    private boolean isAnon;
    private UploadType contentType;
    
    /**
     * Get full name of the uploader.
     * Returns "Anonymous" if the upload is anonymous.
     */
    public String getUploaderFullName() {
        if (isAnon) {
            return "Anonymous";
        }
        if (uploaderFirstName != null && uploaderLastName != null) {
            return uploaderFirstName + " " + uploaderLastName;
        }
        return "Unknown User";
    }
    
    /**
     * Get display name for admin interface.
     * Shows full name with student number in parentheses.
     */
    public String getDisplayName() {
        if (isAnon) {
            return "Anonymous";
        }
        String fullName = getUploaderFullName();
        if (uploaderStudentNumber != null) {
            return fullName + " (" + uploaderStudentNumber + ")";
        }
        return fullName;
    }
}
