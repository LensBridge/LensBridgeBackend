package com.ibrasoft.lensbridge.exception;

import lombok.Getter;

@Getter
public class DailyLimitExceededException extends RuntimeException {
    private final int limit;
    private final long current;

    public DailyLimitExceededException(int limit, long current) {
        super("Daily upload limit of " + limit + " reached (" + current + " uploaded today)");
        this.limit = limit;
        this.current = current;
    }
}
