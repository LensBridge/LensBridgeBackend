package com.ibrasoft.lensbridge.exception;

import lombok.Getter;

@Getter
public class DailyLimitExceededException extends RuntimeException {
    private final int limit;
    private final long current;
    /** The role whose limit applied, lower-case like the keys of {@code uploads.daily-limit.*}. */
    private final String role;

    public DailyLimitExceededException(int limit, long current, String role) {
        super("Daily upload limit of " + limit + " reached (" + current + " uploaded today)");
        this.limit = limit;
        this.current = current;
        this.role = role;
    }
}
