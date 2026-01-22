package com.ibrasoft.lensbridge.exception;

import org.springframework.http.HttpStatus;

public class ApiResponseException extends RuntimeException {
    private final HttpStatus status;
    private final Object body;

    public ApiResponseException(HttpStatus status, Object body) {
        this(status, body, null);
    }

    public ApiResponseException(HttpStatus status, Object body, String message) {
        super(message);
        this.status = status;
        this.body = body;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object getBody() {
        return body;
    }
}
