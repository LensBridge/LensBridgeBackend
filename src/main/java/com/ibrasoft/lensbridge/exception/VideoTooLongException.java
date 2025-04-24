package com.ibrasoft.lensbridge.exception;

public class VideoTooLongException extends RuntimeException {
    public VideoTooLongException(String message) {
        super(message);
    }

    public VideoTooLongException(String message, Throwable cause) {
        super(message, cause);
    }
}
