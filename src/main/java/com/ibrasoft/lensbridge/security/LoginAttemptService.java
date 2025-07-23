package com.ibrasoft.lensbridge.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class LoginAttemptService {

    @Value("${login.maxAttempts:5}")
    private static int MAX_ATTEMPTS;

    @Value("${login.lockdownDurationMinutes:15}")
    private static int LOCKOUT_DURATION_MINUTES;

    @Value("${login.maxCacheSize:1000}")
    private static int MAX_CACHE_SIZE;

    private final ConcurrentMap<String, AttemptRecord> attemptsCache = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredEntries() {
        attemptsCache.entrySet().removeIf(entry -> entry.getValue().isLockoutExpired());
    }

    public void recordFailedAttempt(String key) {
        if (attemptsCache.size() >= MAX_ATTEMPTS) {
            cleanupExpiredEntries();
        }

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
