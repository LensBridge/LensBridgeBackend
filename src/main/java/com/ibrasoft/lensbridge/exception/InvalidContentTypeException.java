package com.ibrasoft.lensbridge.exception;

import lombok.Getter;

@Getter
public class InvalidContentTypeException extends RuntimeException {
    private final String contentType;

    public InvalidContentTypeException(String contentType) {
        super("Content type not allowed: " + contentType);
        this.contentType = contentType;
    }

    /**
     * For rejections where the type alone does not explain the problem — bytes that match no
     * known image signature, or that contradict the type the client declared.
     *
     * @param contentType the type the client declared, kept for callers that inspect it
     * @param detail      the message returned to the client
     */
    public InvalidContentTypeException(String contentType, String detail) {
        super(detail);
        this.contentType = contentType;
    }
}
