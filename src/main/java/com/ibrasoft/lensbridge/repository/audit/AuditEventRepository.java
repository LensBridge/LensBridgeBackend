package com.ibrasoft.lensbridge.repository.audit;

import com.ibrasoft.lensbridge.model.audit.AuditEvent;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.audit.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findAllByOrderByTimestampDesc(Pageable pageable);

    List<AuditEvent> findByAdminIdOrderByTimestampDesc(UUID adminId);

    List<AuditEvent> findByTargetEntityTypeAndTargetEntityIdOrderByTimestampDesc(AuditEntityType entityType, UUID entityId);

    Page<AuditEvent> findByActionOrderByTimestampDesc(AuditAction action, Pageable pageable);

    Page<AuditEvent> findByTimestampBetweenOrderByTimestampDesc(Instant start, Instant end, Pageable pageable);

    long countByAdminId(UUID adminId);

    long countByAction(AuditAction action);
}
