package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.audit.AuditEventDto;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.audit.AuditEntityType;
import com.ibrasoft.lensbridge.model.audit.AuditEvent;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.audit.AuditEventRepository;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditService {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;

    public AuditEvent logAuditEvent(AuditEvent event) {
        try {
            event.setTimestamp(Instant.now());
            AuditEvent savedEvent = auditEventRepository.save(event);
            log.debug("Audit event saved: {} by {} on {}", event.getAction(), event.getAdmin().getEmail(), event.getTargetEntityType());
            return savedEvent;
        } catch (Exception e) {
            log.error("Failed to save audit event: {}", e.getMessage(), e);
            return null;
        }
    }

    public AuditEvent logAuditEvent(String adminEmail, AuditAction action, String entityType, UUID entityId, String IPAddress) {
        AuditEvent event = AuditEvent.builder()
                .admin(userRepository.findByEmail(adminEmail).orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + adminEmail)))
                .action(action)
                .targetEntityType(toEntityType(entityType))
                .targetEntityId(entityId)
                .timestamp(Instant.now())
                .ipAddress(IPAddress)
                .build();
        return logAuditEvent(event);
    }

    public Page<AuditEventDto> getAllAuditEvents(Pageable pageable) {
        return auditEventRepository.findAllByOrderByTimestampDesc(pageable).map(this::toDto);
    }

    public List<AuditEventDto> getAuditEventsByAdmin(UUID adminId) {
        return auditEventRepository.findByAdminIdOrderByTimestampDesc(adminId).stream().map(this::toDto).toList();
    }

    public List<AuditEventDto> getAuditEventsByEntity(String entityType, UUID entityId) {
        return auditEventRepository.findByTargetEntityTypeAndTargetEntityIdOrderByTimestampDesc(toEntityType(entityType), entityId).stream().map(this::toDto).toList();
    }

    public Page<AuditEventDto> getAuditEventsByAction(AuditAction action, Pageable pageable) {
        return auditEventRepository.findByActionOrderByTimestampDesc(action, pageable).map(this::toDto);
    }

    public Page<AuditEventDto> getAuditEventsByDateRange(Instant start, Instant end, Pageable pageable) {
        return auditEventRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable).map(this::toDto);
    }

    public Page<AuditEventDto> getFailedOperations(Pageable pageable) {
        return Page.empty(pageable);
    }

    private AuditEventDto toDto(AuditEvent event) {
        User admin = event.getAdmin();
        String adminName = admin != null ? admin.getFirstName() + " " + admin.getLastName() : null;
        String adminEmail = admin != null ? admin.getEmail() : null;
        UUID adminId = admin != null ? admin.getId() : null;
        return new AuditEventDto(
                event.getId(), event.getAction(), event.getTimestamp(),
                adminId, adminName, adminEmail,
                event.getTargetEntityType(), event.getTargetEntityId(),
                event.getIpAddress(), event.getUserAgent());
    }

    // Statistics methods
    public long getOperationCountByAdmin(UUID adminId) {
        return auditEventRepository.countByAdminId(adminId);
    }

    public long getOperationCountByAction(AuditAction action) {
        return auditEventRepository.countByAction(action);
    }

    private AuditEntityType toEntityType(String entityType) {
        if (entityType == null) return AuditEntityType.EVENT;
        return switch (entityType.toLowerCase()) {
            case "user" -> AuditEntityType.USER;
            case "upload" -> AuditEntityType.UPLOAD;
            case "poster", "calendarevent", "boardevent", "musallah board" -> AuditEntityType.MUSALLAH_BOARD;
            default -> AuditEntityType.EVENT;
        };
    }
}
