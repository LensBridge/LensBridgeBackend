package com.ibrasoft.lensbridge.audit;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@Document(collection = "audit_events")
public class AuditEvent {
    @Id
    private String id;
    private LocalDateTime timestamp;
    private String adminEmail;
    private UUID adminId;
    private AdminAction action;
    private String entityType;
    private UUID entityId;
    private String details;
    private String result;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
}
