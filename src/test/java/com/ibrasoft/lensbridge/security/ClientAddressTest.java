package com.ibrasoft.lensbridge.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

    @Test
    void usesTheRemoteAddressAndIgnoresClientSuppliedForwardingHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(ClientAddress.of(request)).isEqualTo("10.1.2.3");
    }
}
