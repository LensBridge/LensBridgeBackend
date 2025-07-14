package com.bezkoder.springjwt.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class LoginAttemptService {
    
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;
    
    private final ConcurrentMap<String, AttemptRecord> attemptsCache = new ConcurrentHashMap<>();
    
    public void recordFailedAttempt(String key) {
        AttemptRecord record = attemptsCache.computeIfAbsent(key, k -> new AttemptRecord());
        record.incrementAttempts();
    }
    
    public void recordSuccessfulAttempt(String key) {
        attemptsCache.remove(key);
    }
    
    public boolean isBlocked(String key) {
        AttemptRecord record = attemptsCache.get(key);
        if (record == null) {
            return false;
        }
        
        // Check if lockout period has expired
        if (record.isLockoutExpired()) {
            attemptsCache.remove(key);
            return false;
        }
        
        return record.getAttempts() >= MAX_ATTEMPTS;
    }
    
    public int getRemainingAttempts(String key) {
        AttemptRecord record = attemptsCache.get(key);
        if (record == null) {
            return MAX_ATTEMPTS;
        }
        return Math.max(0, MAX_ATTEMPTS - record.getAttempts());
    }
    
    private static class AttemptRecord {
        private int attempts = 0;
        private LocalDateTime lastAttempt = LocalDateTime.now();
        
        public void incrementAttempts() {
            this.attempts++;
            this.lastAttempt = LocalDateTime.now();
        }
        
        public int getAttempts() {
            return attempts;
        }
        
        public boolean isLockoutExpired() {
            return ChronoUnit.MINUTES.between(lastAttempt, LocalDateTime.now()) >= LOCKOUT_DURATION_MINUTES;
        }
    }
}
