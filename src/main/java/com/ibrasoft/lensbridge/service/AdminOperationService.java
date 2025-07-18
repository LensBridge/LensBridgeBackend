package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.audit.AdminAction;
import com.ibrasoft.lensbridge.audit.AuditEvent;
import com.ibrasoft.lensbridge.audit.AdminAuditService;
import com.ibrasoft.lensbridge.dto.response.MessageResponse;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.security.services.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOperationService {
    
    private final UploadService uploadService;
    private final AdminAuditService auditService;
    
    public ResponseEntity<?> executeUploadOperation(
            UUID uploadId, 
            AdminAction operationAction,
            Function<Upload, ResponseEntity<?>> operation,
            HttpServletRequest request) {
        
        UserDetailsImpl admin = getCurrentAdmin();
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        
        try {
            log.info("Admin {} executing {} on upload {}", admin.getEmail(), operationAction, uploadId);
            
            return uploadService.getUploadById(uploadId)
                    .map(upload -> {
                        try {
                            ResponseEntity<?> result = operation.apply(upload);
                            
                            // Log successful audit event
                            logAuditEvent(admin, operationAction, "Upload", uploadId, 
                                    "Success", result.getStatusCode().toString(), ipAddress, userAgent);
                            
                            return result;
                        } catch (Exception e) {
                            log.error("Error during {} operation on upload {}: {}", operationAction, uploadId, e.getMessage(), e);
                            
                            // Log failed audit event
                            logAuditEvent(admin, operationAction, "Upload", uploadId, 
                                    "Failed: " + e.getMessage(), "ERROR", ipAddress, userAgent);
                            
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body(new MessageResponse("Failed to " + operationAction.getDescription().toLowerCase() + ": " + e.getMessage()));
                        }
                    })
                    .orElseGet(() -> {
                        log.warn("Upload not found for {}: {}", operationAction, uploadId);
                        
                        // Log not found audit event
                        logAuditEvent(admin, operationAction, "Upload", uploadId, 
                                "Upload not found", "NOT_FOUND", ipAddress, userAgent);
                        
                        return ResponseEntity.notFound().build();
                    });
                    
        } catch (Exception e) {
            log.error("Unexpected error during {} operation on upload {}: {}", operationAction, uploadId, e.getMessage(), e);
            
            // Log unexpected error audit event
            logAuditEvent(admin, operationAction, "Upload", uploadId, 
                    "Unexpected error: " + e.getMessage(), "ERROR", ipAddress, userAgent);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to " + operationAction.getDescription().toLowerCase() + ": " + e.getMessage()));
        }
    }
    
    private UserDetailsImpl getCurrentAdmin() {
        return (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private void logAuditEvent(UserDetailsImpl admin, AdminAction action, String entityType, 
                              UUID entityId, String details, String result, String ipAddress, String userAgent) {
        AuditEvent event = AuditEvent.builder()
                .adminEmail(admin.getEmail())
                .adminId(admin.getId())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .result(result)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .sessionId(getSessionId())
                .build();
                
        auditService.logAuditEvent(event);
    }
    
    private String getSessionId() {
        return SecurityContextHolder.getContext().getAuthentication().getName() + "_" + System.currentTimeMillis();
    }
}
