package com.ibrasoft.lensbridge.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "lockoutDurationMinutes", 15);
        ReflectionTestUtils.setField(service, "maxCacheSize", 1000);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, Object> cache() {
        return (ConcurrentMap<String, Object>) ReflectionTestUtils.getField(service, "attemptsCache");
    }

    private void setLastAttempt(String key, LocalDateTime when) {
        Object record = cache().get(key);
        ReflectionTestUtils.setField(record, "lastAttempt", when);
    }

    @Test
    void notBlockedWhenNoAttemptsRecorded() {
        assertThat(service.isBlocked("user@x")).isFalse();
        assertThat(service.getRemainingAttempts("user@x")).isEqualTo(5);
    }

    @Test
    void notBlockedBelowMaxAttempts() {
        for (int i = 0; i < 4; i++) {
            service.recordFailedAttempt("user@x");
        }

        assertThat(service.isBlocked("user@x")).isFalse();
        assertThat(service.getRemainingAttempts("user@x")).isEqualTo(1);
    }

    @Test
    void blockedAtExactlyMaxAttempts() {
        for (int i = 0; i < 5; i++) {
            service.recordFailedAttempt("user@x");
        }

        assertThat(service.isBlocked("user@x")).isTrue();
        assertThat(service.getRemainingAttempts("user@x")).isZero();
    }

    @Test
    void remainingAttemptsNeverNegative() {
        for (int i = 0; i < 8; i++) {
            service.recordFailedAttempt("user@x");
        }

        assertThat(service.getRemainingAttempts("user@x")).isZero();
    }

    @Test
    void successfulAttemptClearsRecord() {
        for (int i = 0; i < 5; i++) {
            service.recordFailedAttempt("user@x");
        }
        assertThat(service.isBlocked("user@x")).isTrue();

        service.recordSuccessfulAttempt("user@x");

        assertThat(service.isBlocked("user@x")).isFalse();
        assertThat(service.getRemainingAttempts("user@x")).isEqualTo(5);
    }

    @Test
    void lockoutExpiresAfterLockoutDuration() {
        for (int i = 0; i < 5; i++) {
            service.recordFailedAttempt("user@x");
        }
        assertThat(service.isBlocked("user@x")).isTrue();

        // Simulate the last attempt happening 15 minutes ago (>= lockout window).
        setLastAttempt("user@x", LocalDateTime.now().minusMinutes(15));

        assertThat(service.isBlocked("user@x")).isFalse();
        // isBlocked also evicts the expired entry.
        assertThat(cache()).doesNotContainKey("user@x");
    }

    @Test
    void lockoutStillActiveJustBeforeExpiry() {
        for (int i = 0; i < 5; i++) {
            service.recordFailedAttempt("user@x");
        }
        setLastAttempt("user@x", LocalDateTime.now().minusMinutes(14));

        assertThat(service.isBlocked("user@x")).isTrue();
    }

    @Test
    void cleanupRemovesOnlyExpiredEntries() {
        service.recordFailedAttempt("expired@x");
        service.recordFailedAttempt("fresh@x");
        setLastAttempt("expired@x", LocalDateTime.now().minusMinutes(20));

        service.cleanupExpiredEntries();

        assertThat(cache()).doesNotContainKey("expired@x");
        assertThat(cache()).containsKey("fresh@x");
    }

    @Test
    void cacheSizeLimitTriggersCleanupOfExpiredEntries() {
        ReflectionTestUtils.setField(service, "maxCacheSize", 2);
        service.recordFailedAttempt("a@x");
        service.recordFailedAttempt("b@x");
        // Both entries make the cache reach maxCacheSize. Age one out so the
        // size-triggered cleanup can evict it before adding the next key.
        setLastAttempt("a@x", LocalDateTime.now().minusMinutes(30));

        service.recordFailedAttempt("c@x");

        assertThat(cache()).doesNotContainKey("a@x");
        assertThat(cache()).containsKeys("b@x", "c@x");
    }

    @Test
    void independentKeysTrackedSeparately() {
        for (int i = 0; i < 5; i++) {
            service.recordFailedAttempt("locked@x");
        }
        service.recordFailedAttempt("other@x");

        assertThat(service.isBlocked("locked@x")).isTrue();
        assertThat(service.isBlocked("other@x")).isFalse();
        assertThat(service.getRemainingAttempts("other@x")).isEqualTo(4);
    }

    @Test
    void recordFailedAttemptIsIdempotentOnKeyCreation() {
        service.recordFailedAttempt("user@x");
        service.recordFailedAttempt("user@x");

        Map<String, Object> snapshot = cache();
        assertThat(snapshot).hasSize(1);
        assertThat(service.getRemainingAttempts("user@x")).isEqualTo(3);
    }
}
