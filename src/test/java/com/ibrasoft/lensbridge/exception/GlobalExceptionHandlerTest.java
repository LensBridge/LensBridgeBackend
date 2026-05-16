package com.ibrasoft.lensbridge.exception;

import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.upload.response.DailyLimitErrorResponse;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.dto.upload.response.FileSizeErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Handler methods are invoked
 * directly (no Spring context / MockMvc) to assert the HTTP status and the
 * concrete error-response body produced for each exception.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleApiResponseExceptionUsesStatusAndBodyFromException() {
        Object body = new Object();
        ApiResponseException ex = new ApiResponseException(HttpStatus.I_AM_A_TEAPOT, body);

        ResponseEntity<Object> response = handler.handleApiResponseException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
        assertThat(response.getBody()).isSameAs(body);
    }

    @Test
    void handleRefreshTokenUsesExceptionStatusAndMessage() {
        RefreshTokenException ex = new RefreshTokenException("token expired", HttpStatus.UNAUTHORIZED);

        ResponseEntity<MessageResponse> response = handler.handleRefreshToken(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("token expired");
    }

    @Test
    void handleIllegalArgumentReturnsBadRequestWithMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException("bad arg"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("bad arg");
    }

    @Test
    void handleSecurityReturnsForbiddenWithMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleSecurity(new SecurityException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("nope");
    }

    @Test
    void handleDailyLimitExceededReturnsTooManyRequestsWithLimitDetails() {
        DailyLimitExceededException ex = new DailyLimitExceededException(5, 7);

        ResponseEntity<Object> response = handler.handleDailyLimitExceeded(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isInstanceOf(DailyLimitErrorResponse.class);
        DailyLimitErrorResponse body = (DailyLimitErrorResponse) response.getBody();
        assertThat(body.getError()).isEqualTo("Daily upload limit of 5 reached (7 uploaded today)");
        assertThat(body.getDailyLimit()).isEqualTo(5);
        assertThat(body.getUploadsToday()).isEqualTo(7);
        assertThat(body.getRole()).isEqualTo("unknown");
    }

    @Test
    void handleFileSizeLimitExceededReturnsPayloadTooLargeWithMbConversion() {
        long maxBytes = 10L * 1024 * 1024;
        long actualBytes = 25L * 1024 * 1024;
        FileSizeLimitExceededException ex = new FileSizeLimitExceededException(maxBytes, actualBytes);

        ResponseEntity<Object> response = handler.handleFileSizeLimitExceeded(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isInstanceOf(FileSizeErrorResponse.class);
        FileSizeErrorResponse body = (FileSizeErrorResponse) response.getBody();
        assertThat(body.getError()).isEqualTo("File size 25MB exceeds limit of 10MB");
        assertThat(body.getMaxAllowed()).isEqualTo("10MB");
        assertThat(body.getRequested()).isEqualTo("25MB");
    }

    @Test
    void handleInvalidContentTypeReturnsUnsupportedMediaTypeWithMessage() {
        InvalidContentTypeException ex = new InvalidContentTypeException("application/zip");

        ResponseEntity<Object> response = handler.handleInvalidContentType(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) response.getBody()).getError())
                .isEqualTo("Content type not allowed: application/zip");
    }

    @Test
    void handleEventNotAcceptingUploadsReturnsConflictWithMessage() {
        UUID eventId = UUID.randomUUID();
        EventNotAcceptingUploadsException ex = new EventNotAcceptingUploadsException(eventId);

        ResponseEntity<Object> response = handler.handleEventNotAcceptingUploads(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) response.getBody()).getError())
                .isEqualTo("Event " + eventId + " is not currently accepting uploads");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleMaxUploadSizeExceededReturnsPayloadTooLargeWithStaticBody() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024L);

        ResponseEntity<Map<String, Object>> response =
                handler.handleMaxUploadSizeExceededException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("File Too Large");
        assertThat(body.get("message")).isEqualTo("The uploaded file exceeds the maximum allowed size");
        assertThat(body.get("status")).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

    @Test
    void handleValidationExceptionsMapsFieldErrorsToMessages() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.rejectValue("email", "NotBlank", "must not be blank");
        bindingResult.rejectValue("name", "Size", "too short");

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, String> errors = response.getBody();
        assertThat(errors).isNotNull();
        assertThat(errors).containsEntry("email", "must not be blank");
        assertThat(errors).containsEntry("name", "too short");
    }

    @Test
    void handleUsernameNotFoundReturnsUnauthorizedWithGenericMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleUsernameNotFoundException(new UsernameNotFoundException("user xyz"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void handleBadCredentialsReturnsUnauthorizedWithGenericMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleBadCredentialsException(new BadCredentialsException("wrong"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void handleDisabledReturnsUnauthorizedWithVerifyEmailMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleDisabledException(new DisabledException("disabled"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Account is disabled. Please verify your email.");
    }

    @Test
    void handleAuthorizationDeniedReturnsForbiddenWithGenericMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleAuthorizationDeniedException(
                        new AuthorizationDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied.");
    }

    @Test
    void handleHttpMessageNotReadableReturnsBadRequestWithGenericMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleHttpMessageNotReadableException(
                        new HttpMessageNotReadableException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed JSON request");
    }

    @Test
    void handleHttpRequestMethodNotSupportedReturnsMethodNotAllowed() {
        ResponseEntity<MessageResponse> response =
                handler.handleHttpRequestMethodNotSupportedException(
                        new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getMessage()).isEqualTo("HTTP method not supported");
    }

    @Test
    void handleFileProcessingReturnsInternalServerErrorWithGenericMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleFileProcessingException(
                        new FileProcessingException("disk full"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("Failed to process upload");
    }

    @Test
    void handleGenericExceptionReturnsInternalServerErrorWithGenericMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleGenericException(new RuntimeException("anything"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }
}
