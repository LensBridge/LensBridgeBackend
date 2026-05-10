package com.ibrasoft.lensbridge.model.upload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Represents an uploaded media file, including its metadata and status.
 * This class is akin to the inode models in a filesystem, storing metadata and the location of the uploaded content (R2)
 */
public class Upload {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    private String fileName;
    private String fileUrl;
    private String thumbnailUrl;

    private String uploadDescription;

    private String instagramHandle;
    private UUID uploadedBy;

    private UUID eventId;
    private Instant createdDate;

    private boolean approved;
    private boolean featured;
    private boolean isAnon;

    @Enumerated(EnumType.STRING)
    private UploadType contentType;
}