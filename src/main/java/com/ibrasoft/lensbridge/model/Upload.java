package com.ibrasoft.lensbridge.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;
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
    private String uploadDescription;
    private String instagramHandle;
    private UUID uploadedBy;
    private UUID eventId;
    private LocalDate createdDate;
    private boolean featured;
    private int likes;
    private int views;
    private String contentType;
}