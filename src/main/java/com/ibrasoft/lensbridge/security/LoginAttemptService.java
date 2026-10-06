package com.ibrasoft.lensbridge.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Counts failed sign-ins per key (the email) and locks the key out once it reaches
 * {@code login.maxAttempts}.
 *
 * <p>Counters live in a bounded Caffeine cache. An entry expires one lockout duration after its
 * last failed attempt, which is exactly when the lockout ends, so expiry needs no bookkeeping
 * of our own. The size bound matters because the key is attacker-chosen: spraying random
 * emails would otherwise grow an unbounded map. Increments go through the cache's atomic
 * {@code merge}, so concurrent failures cannot lose a count.
 */
@Component
public class LoginAttemptService {

    private final int maxAttempts;
    private final Cache<String, Integer> attempts;

    @Autowired
    public LoginAttemptService(@Value("${login.maxAttempts:5}") int maxAttempts,
                               @Value("${login.lockdownDurationMinutes:15}") int lockoutDurationMinutes,
                               @Value("${login.maxCacheSize:1000}") int maxCacheSize) {
        this(maxAttempts, lockoutDurationMinutes, maxCacheSize, Ticker.systemTicker());
    }

    /** Lets tests drive the clock instead of sleeping through a lockout. */
    LoginAttemptService(int maxAttempts, int lockoutDurationMinutes, int maxCacheSize, Ticker ticker) {
        this.maxAttempts = maxAttempts;
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(lockoutDurationMinutes))
                .maximumSize(maxCacheSize)
                .ticker(ticker)
                // Run eviction on the calling thread: the work is tiny and this keeps the size
                // bound deterministic instead of lagging behind a burst on a shared pool.
                .executor(Runnable::run)
                .build();
    }

    public void recordFailedAttempt(String key) {
        attempts.asMap().merge(key, 1, Integer::sum);
    }

    public void recordSuccessfulAttempt(String key) {
        attempts.invalidate(key);
    }

    public boolean isBlocked(String key) {
        Integer count = attempts.getIfPresent(key);
        return count != null && count >= maxAttempts;
    }
}
