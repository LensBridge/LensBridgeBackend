package com.ibrasoft.lensbridge.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which address the rate limiter (and the audit log, and a board's last-seen IP) keys on, with
 * requests sent over a real socket to an embedded Tomcat configured the way
 * {@code application.properties.example} configures production: the forwarded-header handling
 * lives in the container (RemoteIpValve), which MockMvc would bypass.
 * <p>
 * The test client always connects from 127.0.0.1, so "untrusted peer" and "trusted proxy" are
 * two configurations of who the trusted proxies are. Each request reports what the application
 * saw, and {@code X-Rate-Limit-Remaining} shows which bucket it was charged to.
 */
class ClientAddressProxyIntegrationTest {

    static final int LIMIT = 5;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();

    /** The shipped example must keep the strategy these tests exercise. */
    @Test
    void exampleConfigurationUsesTheNativeStrategy() throws IOException {
        Properties example = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/application.properties.example"))) {
            example.load(in);
        }
        assertThat(example.getProperty("server.forward-headers-strategy")).isEqualTo("native");
    }

    /** A client talking to the app directly, not through the proxy: its headers are lies. */
    @Nested
    @SpringBootTest(classes = ProxyTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "server.forward-headers-strategy=native",
                    // The proxy is somewhere else; this test's client is not it.
                    "server.tomcat.remoteip.internal-proxies=10\\.255\\.255\\.254",
                    "ratelimit.requests=" + LIMIT})
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class UntrustedPeer {

        @LocalServerPort
        int port;
        @MockitoBean
        UserService userService;

        @Test
        void spoofedXForwardedForDoesNotChangeTheRateLimitKey() throws Exception {
            for (int i = 1; i <= LIMIT; i++) {
                HttpResponse<String> r = get(port, "X-Forwarded-For", "203.0.113." + i);
                assertThat(r.statusCode()).isEqualTo(200);
                assertThat(seen(r).get("clientAddress").asText()).isEqualTo("127.0.0.1");
                // One bucket for all of them: the remaining allowance keeps falling.
                assertThat(remaining(r)).isEqualTo(LIMIT - i);
            }
            HttpResponse<String> over = get(port, "X-Forwarded-For", "198.51.100.77");
            assertThat(over.statusCode()).isEqualTo(429);
        }

        @Test
        void spoofedForwardedForDoesNotChangeTheRateLimitKey() throws Exception {
            for (int i = 1; i <= LIMIT; i++) {
                HttpResponse<String> r = get(port, "Forwarded", "for=203.0.113." + i + ";proto=https");
                assertThat(seen(r).get("clientAddress").asText()).isEqualTo("127.0.0.1");
                assertThat(seen(r).get("secure").asBoolean()).isFalse();
                assertThat(remaining(r)).isEqualTo(LIMIT - i);
            }
            assertThat(get(port, "Forwarded", "for=198.51.100.77").statusCode()).isEqualTo(429);
        }

        @Test
        void spoofedSchemeAndHostAreIgnored() throws Exception {
            HttpResponse<String> r = get(port, "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", "evil.example", "X-Forwarded-Port", "443");
            JsonNode seen = seen(r);
            assertThat(seen.get("scheme").asText()).isEqualTo("http");
            assertThat(seen.get("secure").asBoolean()).isFalse();
            assertThat(seen.get("serverName").asText()).isEqualTo("127.0.0.1");
            assertThat(seen.get("serverPort").asInt()).isEqualTo(port);
        }
    }

    /** nginx on the same host or the Docker network: Tomcat's default trusted ranges apply. */
    @Nested
    @SpringBootTest(classes = ProxyTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"server.forward-headers-strategy=native", "ratelimit.requests=" + LIMIT})
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class TrustedProxy {

        @LocalServerPort
        int port;
        @MockitoBean
        UserService userService;

        @Test
        void proxysXForwardedForIsTheClientAndEachClientHasItsOwnBucket() throws Exception {
            for (int i = 1; i <= LIMIT + 2; i++) {
                HttpResponse<String> r = get(port, "X-Forwarded-For", "203.0.113." + i);
                assertThat(r.statusCode()).isEqualTo(200);
                assertThat(seen(r).get("clientAddress").asText()).isEqualTo("203.0.113." + i);
                assertThat(remaining(r)).isEqualTo(LIMIT - 1);
            }
        }

        /**
         * A proxy that appends ($proxy_add_x_forwarded_for) passes the client's own entries
         * through. The address is read from the right, so those are never used: rotating them
         * still charges the one real client.
         */
        @Test
        void entriesTheClientPrependedAreIgnored() throws Exception {
            for (int i = 1; i <= LIMIT; i++) {
                HttpResponse<String> r = get(port, "X-Forwarded-For", "198.51.100." + i + ", 203.0.113.9");
                assertThat(seen(r).get("clientAddress").asText()).isEqualTo("203.0.113.9");
                assertThat(remaining(r)).isEqualTo(LIMIT - i);
            }
            assertThat(get(port, "X-Forwarded-For", "198.51.100.99, 203.0.113.9").statusCode()).isEqualTo(429);
        }

        @Test
        void proxysSchemeHostAndPortAreHonoured() throws Exception {
            JsonNode seen = seen(get(port, "X-Forwarded-For", "203.0.113.1", "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", "api.example.org", "X-Forwarded-Port", "443"));
            assertThat(seen.get("scheme").asText()).isEqualTo("https");
            assertThat(seen.get("secure").asBoolean()).isTrue();
            assertThat(seen.get("serverName").asText()).isEqualTo("api.example.org");
            assertThat(seen.get("serverPort").asInt()).isEqualTo(443);
        }

        /** Only X-Forwarded-For is read; the RFC 7239 header is not, even from the proxy. */
        @Test
        void forwardedHeaderIsNotRead() throws Exception {
            JsonNode seen = seen(get(port, "Forwarded", "for=203.0.113.5"));
            assertThat(seen.get("clientAddress").asText()).isEqualTo("127.0.0.1");
        }
    }

    static HttpResponse<String> get(int port, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/test/client"));
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode seen(HttpResponse<String> response) throws IOException {
        assertThat(response.statusCode()).isEqualTo(200);
        return MAPPER.readTree(response.body());
    }

    static long remaining(HttpResponse<String> response) {
        return Long.parseLong(response.headers().firstValue("X-Rate-Limit-Remaining").orElseThrow());
    }
}
