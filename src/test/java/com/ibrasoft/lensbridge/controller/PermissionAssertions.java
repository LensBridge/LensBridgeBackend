package com.ibrasoft.lensbridge.controller;

import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared assertions for class-level {@code @PreAuthorize} permission tests: a wrong-role request
 * must be 403, and an authorized request must be neither 401 nor 403 (the endpoint logic itself is
 * out of scope for security tests).
 */
final class PermissionAssertions {

    private PermissionAssertions() {
    }

    static void expectAuthorizationOutcome(ResultActions actions, boolean expectForbidden) throws Exception {
        if (expectForbidden) {
            actions.andExpect(status().isForbidden());
            return;
        }
        actions.andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 401 || s == 403) {
                throw new AssertionError("Expected ROOT to be authorized but got HTTP " + s);
            }
        });
    }
}
