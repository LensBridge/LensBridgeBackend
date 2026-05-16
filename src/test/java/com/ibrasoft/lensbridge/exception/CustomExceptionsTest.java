package com.ibrasoft.lensbridge.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the custom exception types: verifies constructed message,
 * exposed accessors, and runtime-exception nature.
 */
class CustomExceptionsTest {

    @Test
    void apiResponseExceptionTwoArgConstructorStoresStatusAndBodyWithNullMessage() {
        Object body = new Object();

        ApiResponseException ex = new ApiResponseException(HttpStatus.BAD_GATEWAY, body);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getBody()).isSameAs(body);
        assertThat(ex.getMessage()).isNull();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void apiResponseExceptionThreeArgConstructorStoresMessage() {
        ApiResponseException ex =
                new ApiResponseException(HttpStatus.NOT_FOUND, "body", "missing thing");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getBody()).isEqualTo("body");
        assertThat(ex.getMessage()).isEqualTo("missing thing");
    }

    @Test
    void dailyLimitExceededExceptionBuildsMessageAndExposesCounts() {
        DailyLimitExceededException ex = new DailyLimitExceededException(3, 4);

        assertThat(ex.getLimit()).isEqualTo(3);
        assertThat(ex.getCurrent()).isEqualTo(4L);
        assertThat(ex.getMessage()).isEqualTo("Daily upload limit of 3 reached (4 uploaded today)");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void eventNotAcceptingUploadsExceptionBuildsMessageAndExposesEventId() {
        UUID eventId = UUID.randomUUID();

        EventNotAcceptingUploadsException ex = new EventNotAcceptingUploadsException(eventId);

        assertThat(ex.getEventId()).isEqualTo(eventId);
        assertThat(ex.getMessage())
                .isEqualTo("Event " + eventId + " is not currently accepting uploads");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void fileProcessingExceptionStoresMessage() {
        FileProcessingException ex = new FileProcessingException("could not write file");

        assertThat(ex.getMessage()).isEqualTo("could not write file");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void fileSizeLimitExceededExceptionBuildsMessageWithMbAndExposesBytes() {
        long maxBytes = 8L * 1024 * 1024;
        long actualBytes = 20L * 1024 * 1024;

        FileSizeLimitExceededException ex =
                new FileSizeLimitExceededException(maxBytes, actualBytes);

        assertThat(ex.getMaxBytes()).isEqualTo(maxBytes);
        assertThat(ex.getActualBytes()).isEqualTo(actualBytes);
        assertThat(ex.getMessage()).isEqualTo("File size 20MB exceeds limit of 8MB");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void imageProcessingExceptionStoresMessage() {
        ImageProcessingException ex = new ImageProcessingException("bad image");

        assertThat(ex.getMessage()).isEqualTo("bad image");
        assertThat(ex.getCause()).isNull();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void imageProcessingExceptionStoresMessageAndCause() {
        Throwable cause = new IllegalStateException("root");

        ImageProcessingException ex = new ImageProcessingException("wrap", cause);

        assertThat(ex.getMessage()).isEqualTo("wrap");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void invalidContentTypeExceptionBuildsMessageAndExposesContentType() {
        InvalidContentTypeException ex = new InvalidContentTypeException("text/plain");

        assertThat(ex.getContentType()).isEqualTo("text/plain");
        assertThat(ex.getMessage()).isEqualTo("Content type not allowed: text/plain");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void refreshTokenExceptionStoresMessageAndStatus() {
        RefreshTokenException ex =
                new RefreshTokenException("refresh token not found", HttpStatus.FORBIDDEN);

        assertThat(ex.getMessage()).isEqualTo("refresh token not found");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
