package com.ibrasoft.lensbridge.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {
    
    List<AuditEvent> findByAdminIdOrderByTimestampDesc(UUID adminId);
    
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, UUID entityId);
    
    Page<AuditEvent> findByActionOrderByTimestampDesc(AdminAction action, Pageable pageable);
    
    @Query("{'timestamp': {'$gte': ?0, '$lte': ?1}}")
    Page<AuditEvent> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    @Query("{'result': {'$regex': '^ERROR|FAILED', '$options': 'i'}}")
    Page<AuditEvent> findFailedOperationsOrderByTimestampDesc(Pageable pageable);
    
    Page<AuditEvent> findAllByOrderByTimestampDesc(Pageable pageable);
    
    long countByAdminId(UUID adminId);
    
    long countByAction(AdminAction action);
}
