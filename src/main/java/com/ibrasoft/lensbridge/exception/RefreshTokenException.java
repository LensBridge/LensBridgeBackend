package com.ibrasoft.lensbridge.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenException extends RuntimeException {
    private final HttpStatus status;

    public RefreshTokenException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
