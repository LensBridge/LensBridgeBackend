package com.ibrasoft.lensbridge.exception;

import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.upload.response.DailyLimitErrorResponse;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.dto.upload.response.FileSizeErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extends {@link ResponseEntityExceptionHandler} so Spring's own request-handling
 * failures keep their intended status codes.
 * <p>
 * Without it, {@link #handleGenericException} below was the only handler that matched
 * them, and {@code ExceptionHandlerExceptionResolver} runs ahead of
 * {@code DefaultHandlerExceptionResolver} — so a request that was merely malformed came
 * back as a 500 "An unexpected error occurred". That masked a real incident: a client
 * sending a signup body with no {@code Content-Type} header produced a 500 rather than a
 * 415, and the response said nothing about the header being the problem.
 * <p>
 * Bodies stay as {@link MessageResponse} rather than the {@code ProblemDetail} the parent
 * class produces by default, because the frontend reads {@code error.message} everywhere.
 * {@link #handleExceptionInternal} performs that conversion in one place.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Rewrites every body produced by the parent class into a {@link MessageResponse},
     * leaving the status and headers it chose alone.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response == null) {
            return null; // response already committed; the parent declined to write one
        }
        return new ResponseEntity<>(asMessage(response.getBody(), ex), response.getHeaders(),
                response.getStatusCode());
    }

    private static MessageResponse asMessage(@Nullable Object body, Exception ex) {
        if (body instanceof MessageResponse message) {
            return message; // an override below already picked the wording
        }
        if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
            return new MessageResponse(problem.getDetail());
        }
        return new MessageResponse(ex.getMessage() != null ? ex.getMessage() : "Request could not be processed");
    }

    /**
     * Names the header that is wrong. The default message reports the media type the
     * server inferred, which for a request with no {@code Content-Type} at all is
     * {@code application/octet-stream} — accurate, and useless to whoever has to fix
     * the client.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String supported = ex.getSupportedMediaTypes().stream()
                .map(MediaType::toString)
                .collect(Collectors.joining(", "));
        String message = ex.getContentType() == null
                ? "Request is missing a Content-Type header"
                : "Content-Type '" + ex.getContentType() + "' is not supported";
        if (!supported.isEmpty()) {
            message += "; this endpoint accepts " + supported;
        }
        log.warn("Rejected {}: {}", request.getDescription(false), message);
        return handleExceptionInternal(ex, new MessageResponse(message), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String details = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                        : error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return handleExceptionInternal(ex, new MessageResponse(details), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Malformed JSON request: ", ex);
        return handleExceptionInternal(ex, new MessageResponse("Malformed JSON request"), headers, status, request);
    }

    /**
     * Sets {@code Allow}, which RFC 9110 requires on a 405 and which the parent
     * implementation would have set for us. Overriding it to swap the body means taking
     * that on: without the header a client has no machine-readable way to discover that
     * the path exists and only its method was wrong.
     */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        String message = "HTTP method not supported";
        if (!CollectionUtils.isEmpty(supported)) {
            headers.setAllow(supported);
            message += "; this endpoint accepts " + supported.stream()
                    .map(HttpMethod::name)
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
        return handleExceptionInternal(ex, new MessageResponse(message), headers, status, request);
    }

    /** Keeps the pre-existing body shape; this one predates the MessageResponse convention. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Multipart upload too large: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "File Too Large");
        body.put("message", "The uploaded file exceeds the maximum allowed size");
        body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        return new ResponseEntity<>(body, headers, HttpStatus.PAYLOAD_TOO_LARGE);
    }
    
    @ExceptionHandler(ApiResponseException.class)
    public ResponseEntity<Object> handleApiResponseException(ApiResponseException ex) {
        Object body = ex.getBody() instanceof ErrorResponse error
                ? new MessageResponse(error.getError())
                : ex.getBody();
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<MessageResponse> handleRefreshToken(RefreshTokenException ex) {
        log.warn("Refresh token rejected: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<MessageResponse> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(DailyLimitExceededException.class)
    public ResponseEntity<Object> handleDailyLimitExceeded(DailyLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(DailyLimitErrorResponse.of(ex.getMessage(), ex.getLimit(), ex.getCurrent(), "unknown"));
    }

    @ExceptionHandler(FileSizeLimitExceededException.class)
    public ResponseEntity<Object> handleFileSizeLimitExceeded(FileSizeLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(FileSizeErrorResponse.of(ex.getMessage(),
                        (ex.getMaxBytes() / 1024 / 1024) + "MB",
                        (ex.getActualBytes() / 1024 / 1024) + "MB"));
    }

    @ExceptionHandler(InvalidContentTypeException.class)
    public ResponseEntity<Object> handleInvalidContentType(InvalidContentTypeException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(EventNotAcceptingUploadsException.class)
    public ResponseEntity<Object> handleEventNotAcceptingUploads(EventNotAcceptingUploadsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<MessageResponse> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        log.warn("Authentication failed - user not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Invalid username or password"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<MessageResponse> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Authentication failed - bad credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Invalid username or password"));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<MessageResponse> handleDisabledException(DisabledException ex) {
        log.warn("Authentication failed - account disabled: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Account is disabled. Please verify your email."));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<MessageResponse> handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        log.warn("Authorization failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Access denied."));
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<MessageResponse> handleFileProcessingException(FileProcessingException ex) {
        log.error("File processing error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Failed to process upload"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("An unexpected error occurred"));
    }
}
