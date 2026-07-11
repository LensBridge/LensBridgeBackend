package com.ibrasoft.minbar.audit.dto;

import com.ibrasoft.minbar.audit.model.AuditAction;
import com.ibrasoft.minbar.audit.model.AuditEntityType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventDto {
    private UUID id;
    private AuditAction action;
    private Instant timestamp;
    private UUID adminId;
    private String adminName;
    private String adminEmail;
    private AuditEntityType targetEntityType;
    private UUID targetEntityId;
    private String ipAddress;
    private String userAgent;
}
