package com.bezkoder.springjwt.security.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AuditService {
    
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public void logSecurityEvent(String event, String username, String details) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logMessage = String.format("[%s] %s - User: %s - Details: %s", 
                                        timestamp, event, username, details);
        auditLogger.info(logMessage);
    }
    
    public void logLoginAttempt(String username, boolean successful, String ipAddress) {
        String event = successful ? "LOGIN_SUCCESS" : "LOGIN_FAILED";
        String details = "IP: " + ipAddress;
        logSecurityEvent(event, username, details);
    }
    
    public void logPasswordChange(String username, String ipAddress) {
        logSecurityEvent("PASSWORD_CHANGED", username, "IP: " + ipAddress);
    }
    
    public void logEmailVerification(String username, String email) {
        logSecurityEvent("EMAIL_VERIFIED", username, "Email: " + email);
    }
    
    public void logPasswordReset(String username, String email) {
        logSecurityEvent("PASSWORD_RESET_REQUESTED", username, "Email: " + email);
    }
}
