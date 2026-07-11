package com.ibrasoft.minbar.shared.exception;

import lombok.Getter;

@Getter
public class InvalidContentTypeException extends RuntimeException {
    private final String contentType;

    public InvalidContentTypeException(String contentType) {
        super("Content type not allowed: " + contentType);
        this.contentType = contentType;
    }
}
