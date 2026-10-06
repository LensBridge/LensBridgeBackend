package com.ibrasoft.lensbridge.security.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEntryPointJwtTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AuthEntryPointJwt entryPoint = new AuthEntryPointJwt(mapper);

    @Test
    void writesTheUnauthorizedBodyWithStatusErrorMessageAndPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/things");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("nope"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("message").asText()).isEqualTo("nope");
        assertThat(body.get("path").asText()).isEqualTo("/api/things");
        assertThat(body.size()).isEqualTo(4);
    }
}
