package com.ibrasoft.lensbridge.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.UUID;

@Data
@Document(collection = "uploads")
public class Upload {
    @Id
    private UUID uuid;
    private String fileName;
    private String fileUrl;
    private String instagramHandle;
    private String eventId;
}