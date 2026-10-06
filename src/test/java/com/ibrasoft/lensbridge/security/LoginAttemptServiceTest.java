package com.ibrasoft.lensbridge.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private AtomicLong nanos;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        nanos = new AtomicLong();
        service = serviceWith(1000);
    }

    private LoginAttemptService serviceWith(int maxCacheSize) {
        Ticker ticker = nanos::get;
        return new LoginAttemptService(5, 15, maxCacheSize, ticker);
    }

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    private void fail(String key, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailedAttempt(key);
        }
    }

    @SuppressWarnings("unchecked")
    private Cache<String, Integer> cache() {
        return (Cache<String, Integer>) ReflectionTestUtils.getField(service, "attempts");
    }

    @Test
    void notBlockedWhenNoAttemptsRecorded() {
        assertThat(service.isBlocked("user@x")).isFalse();
    }

    @Test
    void notBlockedBelowMaxAttempts() {
        fail("user@x", 4);

        assertThat(service.isBlocked("user@x")).isFalse();
    }

    @Test
    void blockedAtExactlyMaxAttempts() {
        fail("user@x", 5);

        assertThat(service.isBlocked("user@x")).isTrue();
    }

    @Test
    void successfulAttemptClearsRecord() {
        fail("user@x", 5);
        assertThat(service.isBlocked("user@x")).isTrue();

        service.recordSuccessfulAttempt("user@x");

        assertThat(service.isBlocked("user@x")).isFalse();
    }

    @Test
    void lockoutExpiresAfterLockoutDuration() {
        fail("user@x", 5);
        assertThat(service.isBlocked("user@x")).isTrue();

        advance(Duration.ofMinutes(15));

        assertThat(service.isBlocked("user@x")).isFalse();
    }

    @Test
    void lockoutStillActiveJustBeforeExpiry() {
        fail("user@x", 5);

        advance(Duration.ofMinutes(14));

        assertThat(service.isBlocked("user@x")).isTrue();
    }

    @Test
    void eachFailureRestartsTheLockoutWindow() {
        fail("user@x", 5);
        advance(Duration.ofMinutes(14));
        service.recordFailedAttempt("user@x");
        advance(Duration.ofMinutes(14));

        // 28 minutes after the first failure, but only 14 after the latest.
        assertThat(service.isBlocked("user@x")).isTrue();
    }

    @Test
    void countRestartsAfterTheWindowHasLapsed() {
        fail("user@x", 4);
        advance(Duration.ofMinutes(15));

        fail("user@x", 4);

        assertThat(service.isBlocked("user@x")).isFalse();
    }

    @Test
    void independentKeysTrackedSeparately() {
        fail("locked@x", 5);
        service.recordFailedAttempt("other@x");

        assertThat(service.isBlocked("locked@x")).isTrue();
        assertThat(service.isBlocked("other@x")).isFalse();
    }

    @Test
    void cacheIsBoundedSoEmailSprayingCannotGrowItWithoutLimit() {
        service = serviceWith(10);

        for (int i = 0; i < 500; i++) {
            service.recordFailedAttempt("spray" + i + "@x");
        }
        cache().cleanUp();

        assertThat(cache().estimatedSize()).isLessThanOrEqualTo(10);
    }

    @Test
    void concurrentFailuresDoNotLoseCounts() throws Exception {
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < perThread; i++) {
                    service.recordFailedAttempt("user@x");
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(cache().getIfPresent("user@x")).isEqualTo(threads * perThread);
    }
}
