package com.ibrasoft.lensbridge.exception;

import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.dto.upload.response.DailyLimitErrorResponse;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring's own request-handling failures must keep their status codes.
 * <p>
 * These used to be swallowed by the catch-all {@code @ExceptionHandler(Exception.class)},
 * which {@code ExceptionHandlerExceptionResolver} reaches before Spring's
 * {@code DefaultHandlerExceptionResolver} gets a chance to assign the right one. A client
 * that posted a JSON body without a {@code Content-Type} header therefore got a 500
 * reading "An unexpected error occurred", which pointed the investigation at the server.
 */
class GlobalExceptionHandlerTest {

    private static final String URL = "/test/echo";
    private static final String CONFLICT_URL = "/test/conflict";
    private static final String PASSTHROUGH_URL = "/test/passthrough";

    private MockMvc mockMvc;

    @Data
    static class Payload {
        @NotBlank
        private String name;
    }

    @RestController
    static class StubController {
        @PostMapping(URL)
        MessageResponse echo(@Valid @RequestBody Payload payload) {
            return new MessageResponse(payload.getName());
        }

        @GetMapping(CONFLICT_URL)
        MessageResponse conflict() {
            throw new ApiResponseException(HttpStatus.CONFLICT,
                    ErrorResponse.of("That instagram URL is already promoted"));
        }

        @GetMapping(PASSTHROUGH_URL)
        MessageResponse passthrough() {
            throw new ApiResponseException(HttpStatus.TOO_MANY_REQUESTS,
                    DailyLimitErrorResponse.of("Daily limit reached", 5, 5, "unknown"));
        }
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * The production incident: Firefox sent the signup body with no Content-Type, Spring
     * substituted application/octet-stream, and the client saw a 500.
     */
    @Test
    void aBodyWithNoContentTypeIsA415ThatNamesTheMissingHeader() throws Exception {
        mockMvc.perform(post(URL).content("{\"name\":\"T\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message", containsString("Content-Type")));
    }

    @Test
    void anUnsupportedContentTypeIsA415ThatNamesTheAcceptedTypes() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"T\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message", containsString("application/json")));
    }

    /**
     * The Allow header is what tells a client the path exists and only the method was
     * wrong. Overriding the parent handler to swap the body means setting it here, since
     * the parent's own implementation — which does set it — no longer runs.
     */
    @Test
    void theWrongMethodIsA405ThatAdvertisesTheAllowedOnes() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.message", containsString("POST")));
    }

    @Test
    void unparseableJsonIsA400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    /** Field errors keep the "field: message" formatting the frontend surfaces verbatim. */
    @Test
    void aFailedConstraintIsA400NamingTheField() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("name:")));
    }

    /**
     * Services throw with an {@code ErrorResponse}, which serializes as {@code {"error": ...}},
     * but the spec documents every operation's error as {@code MessageResponse} and every
     * generated client reads {@code .message}. The mismatch meant each specific message a
     * service wrote arrived as {@code undefined} and the operator saw a generic fallback
     * instead — the 409 below read "Failed to create social" rather than naming the duplicate.
     */
    @Test
    void anErrorResponseBodyIsPublishedUnderTheDocumentedMessageKey() throws Exception {
        mockMvc.perform(get(CONFLICT_URL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That instagram URL is already promoted"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    /**
     * Bodies carrying fields beyond a message keep every one of them — a client showing
     * "you have used 5 of 5 uploads today" needs the counts, and flattening to a message
     * would leave it with prose to parse.
     */
    @Test
    void aRicherErrorBodyPassesThroughUntouched() throws Exception {
        mockMvc.perform(get(PASSTHROUGH_URL))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.dailyLimit").value(5))
                .andExpect(jsonPath("$.uploadsToday").value(5))
                .andExpect(jsonPath("$.error").value("Daily limit reached"));
    }
}
