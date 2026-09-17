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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the request binding for POST /api/auth/reset-password.
 *
 * The token and new password moved out of the query string and into the request
 * body, because a query string is written to access logs, proxy logs and browser
 * history. These tests exercise the real bind-and-validate path so a regression
 * back to @RequestParam fails here.
 */
class AuthControllerResetPasswordTest {

    private static final String URL = "/api/auth/reset-password";

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
                mock(RefreshTokenService.class),
                mock(com.ibrasoft.lensbridge.security.services.AuthorityResolver.class));

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsTokenAndPasswordInTheRequestBody() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token\",\"newPassword\":\"correct horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully."));

        verify(userService).resetPassword("reset-token", "correct horse");
    }

    /**
     * The shape this endpoint used to accept. It must now fail: if query parameters
     * still worked, callers could keep putting a password in the URL.
     */
    @Test
    void rejectsCredentialsSuppliedAsQueryParameters() throws Exception {
        mockMvc.perform(post(URL)
                        .param("token", "reset-token")
                        .param("newPassword", "correct horse"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"correct horse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(userService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void rejectsBlankPassword() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token\",\"newPassword\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void rejectsPasswordShorterThanTheMinimum() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(userService, never()).resetPassword(anyString(), anyString());
    }

    /**
     * Errors carry a MessageResponse like every other failure in this API, so the
     * frontend's `error.message` shows the real reason.
     */
    @Test
    void validationFailuresReturnAMessageResponse() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\",\"newPassword\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
    }
}
