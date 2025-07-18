package com.ibrasoft.lensbridge.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditService {
    
    private final AuditEventRepository auditEventRepository;
    
    public AuditEvent logAuditEvent(AuditEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            AuditEvent savedEvent = auditEventRepository.save(event);
            log.debug("Audit event saved: {} by {} on {}", event.getAction(), event.getAdminEmail(), event.getEntityType());
            return savedEvent;
        } catch (Exception e) {
            log.error("Failed to save audit event: {}", e.getMessage(), e);
            return null;
        }
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
    
    public Page<AuditEvent> getAuditEventsByAction(AdminAction action, Pageable pageable) {
        return auditEventRepository.findByActionOrderByTimestampDesc(action, pageable);
    }
    
    public Page<AuditEvent> getAuditEventsByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return auditEventRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable);
    }
    
    public Page<AuditEvent> getFailedOperations(Pageable pageable) {
        return auditEventRepository.findFailedOperationsOrderByTimestampDesc(pageable);
    }
    
    // Statistics methods
    public long getOperationCountByAdmin(UUID adminId) {
        return auditEventRepository.countByAdminId(adminId);
    }
    
    public long getOperationCountByAction(AdminAction action) {
        return auditEventRepository.countByAction(action);
    }
}
