package com.ibrasoft.lensbridge.repository.audit;

import com.ibrasoft.lensbridge.model.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

}
