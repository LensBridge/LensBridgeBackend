package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.exception.GlobalExceptionHandler;
import com.ibrasoft.lensbridge.security.LoginAttemptService;
import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.service.RefreshTokenService;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the request binding for POST /api/auth/validate-reset-token.
 *
 * This endpoint reads rather than writes, but it is a POST: the reset token is a
 * credential, and only a request body keeps it out of access logs, proxy logs and
 * browser history. These tests exercise the real bind-and-validate path so a
 * regression back to a GET with @RequestParam fails here.
 */
class AuthControllerValidateResetTokenTest {

    private static final String URL = "/api/auth/validate-reset-token";

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);

        AuthController controller = new AuthController(
                mock(AuthenticationManager.class),
                userService,
                mock(JwtUtils.class),
                mock(LoginAttemptService.class),
                mock(RefreshTokenService.class));

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsTokenInTheRequestBody() throws Exception {
        when(userService.validateResetToken("reset-token")).thenReturn(true);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token is valid."));

        verify(userService).validateResetToken("reset-token");
    }

    /**
     * The shape this endpoint used to accept. A GET cannot carry a body, so if this
     * ever succeeds again the token is back in the query string.
     */
    @Test
    void noLongerAcceptsAGetWithTheTokenInTheQueryString() throws Exception {
        mockMvc.perform(get(URL).param("token", "reset-token"))
                .andExpect(status().isMethodNotAllowed());

        verify(userService, never()).validateResetToken(anyString());
    }

    @Test
    void rejectsQueryParametersOnThePost() throws Exception {
        mockMvc.perform(post(URL).param("token", "reset-token"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).validateResetToken(anyString());
    }

    @Test
    void rejectsBlankToken() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(userService, never()).validateResetToken(anyString());
    }

    @Test
    void rejectsExpiredOrUnknownToken() throws Exception {
        when(userService.validateResetToken("stale")).thenReturn(false);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"stale\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired token."));
    }
}
