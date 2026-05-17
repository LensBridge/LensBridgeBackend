package com.ibrasoft.lensbridge.exception;

import lombok.Getter;

@Getter
public class FileSizeLimitExceededException extends RuntimeException {
    private final long maxBytes;
    private final long actualBytes;

    public FileSizeLimitExceededException(long maxBytes, long actualBytes) {
        super("File size " + (actualBytes / 1024 / 1024) + "MB exceeds limit of " + (maxBytes / 1024 / 1024) + "MB");
        this.maxBytes = maxBytes;
        this.actualBytes = actualBytes;
    }
}
