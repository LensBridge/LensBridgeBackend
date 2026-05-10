package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.audit.AuditEvent;
import com.ibrasoft.lensbridge.repository.audit.AuditEventRepository;

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
        AuditEvent event = AuditEvent.builder().adminEmail(adminEmail).action(action).entityType(entityType).entityId(entityId).timestamp(Instant.now()).ipAddress(IPAddress).build();
        return logAuditEvent(event);
    }

    public Page<AuditEvent> getAllAuditEvents(Pageable pageable) {
        return auditEventRepository.findAllByOrderByTimestampDesc(pageable);
    }

    public List<AuditEvent> getAuditEventsByAdmin(UUID adminId) {
        return auditEventRepository.findByAdminIdOrderByTimestampDesc(adminId);
    }

    public List<AuditEvent> getAuditEventsByEntity(String entityType, UUID entityId) {
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
    }

    public Page<AuditEvent> getAuditEventsByAction(AuditAction action, Pageable pageable) {
        return auditEventRepository.findByActionOrderByTimestampDesc(action, pageable);
    }

    public Page<AuditEvent> getAuditEventsByDateRange(Instant start, Instant end, Pageable pageable) {
        return auditEventRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable);
    }

    public Page<AuditEvent> getFailedOperations(Pageable pageable) {
        return auditEventRepository.findFailedOperationsOrderByTimestampDesc(pageable);
    }

    // Statistics methods
    public long getOperationCountByAdmin(UUID adminId) {
        return auditEventRepository.countByAdminId(adminId);
    }

    public long getOperationCountByAction(AuditAction action) {
        return auditEventRepository.countByAction(action);
    }
}
