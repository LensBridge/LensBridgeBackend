package com.ibrasoft.lensbridge.model.upload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Id;

import com.ibrasoft.lensbridge.model.auth.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an uploaded media file, including its metadata and status.
 * This class is akin to the inode models in a filesystem, storing metadata and the location of the uploaded content (R2)
 */
@Entity
@Table(name = "uploads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Upload {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    private String fileName; 
    private String fileUrl;
    private String thumbnailUrl;

    private String uploadDescription;

    private String instagramHandle;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id")
    private MediaEvent mediaEvent;

    private Instant createdDate;

    private boolean approved;
    private boolean featured;
    private boolean isAnon;

    @Enumerated(EnumType.STRING)
    private UploadType contentType;

    /**
     * Soft-delete marker: rows are never removed, only stamped here and filtered out of queries.
     * deletedAt = null => not deleted.
     */
    @Column(nullable = true)
    private Instant deletedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    private User deletedBy;    
}