package com.ibrasoft.lensbridge.model.upload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "uploads")
public class Upload {
    @Id
    private UUID uuid;

    private String fileName;
    private String fileUrl;
    private String thumbnailUrl;
    private String uploadDescription;

    private String instagramHandle;
    private UUID uploadedBy;

    private UUID eventId;
    private LocalDateTime createdDate;

    private boolean approved;
    private boolean featured;
    private boolean isAnon;

    private UploadType contentType;
}