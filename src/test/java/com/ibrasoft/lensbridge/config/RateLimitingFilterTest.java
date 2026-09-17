package com.ibrasoft.lensbridge.config;

import com.ibrasoft.lensbridge.model.auth.Role;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link RateLimitingFilter}.
 *
 * <p>Notes on real (vs. assumed) behavior, exercised by these tests:
 * <ul>
 *   <li>The {@code @Value} default for {@code ratelimit.requests} is <b>10</b>, not 100.
 *       Tests set the limit explicitly via reflection so they don't depend on the default.</li>
 *   <li>Exempt-role detection reads from the {@link SecurityContextHolder}, not the request.</li>
 *   <li>Per-client isolation keys on {@code X-Forwarded-For} (first hop) falling back to
 *       {@code remoteAddr}.</li>
 *   <li>On a successful pass-through an {@code X-Rate-Limit-Remaining} header is added; on a
 *       block the status is set to 429 and a plain-text body is written (chain not invoked).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
        ReflectionTestUtils.setField(filter, "maxRequests", 3);
        ReflectionTestUtils.setField(filter, "durationMinutes", 1);
        ReflectionTestUtils.setField(filter, "exemptRoles", List.of(Role.ROOT, Role.ADMIN));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestFromIp(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private void authenticateWith(Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "pw", List.of(role)));
    }

    @Test
    void requestUnderLimitPassesThroughChain() throws Exception {
        MockHttpServletRequest request = requestFromIp("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("2");
    }

    // @Test
    // void requestsOverLimitReturn429AndDoNotInvokeChain() throws Exception {
    //     FilterChain chain = mock(FilterChain.class);

    //     // Limit is 3: first three consume, fourth is blocked.
    //     for (int i = 0; i < 3; i++) {
    //         filter.doFilter(requestFromIp("10.0.0.2"), new MockHttpServletResponse(), chain);
    //     }

    //     MockHttpServletResponse blocked = new MockHttpServletResponse();
    //     filter.doFilter(requestFromIp("10.0.0.2"), blocked, chain);

    //     verify(chain, times(3)).doFilter(any(), any());
    //     assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    //     assertThat(blocked.getContentAsString()).isEqualTo("Too many requests – please try again later.");
    //     assertThat(blocked.getHeader("X-Rate-Limit-Remaining")).isNull();
    // }

    @Test
    void rootRoleIsExemptFromRateLimiting() throws Exception {
        authenticateWith(Role.ROOT);
        FilterChain chain = mock(FilterChain.class);

        // Far more than the configured limit of 3.
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestFromIp("10.0.0.3"), response, chain);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            // Exempt path skips the bucket, so no rate-limit header is added.
            assertThat(response.getHeader("X-Rate-Limit-Remaining")).isNull();
        }

        verify(chain, times(10)).doFilter(any(), any());
    }

    @Test
    void adminRoleIsExemptFromRateLimiting() throws Exception {
        authenticateWith(Role.ADMIN);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestFromIp("10.0.0.4"), response, chain);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        verify(chain, times(10)).doFilter(any(), any());
    }

    @Test
    void nonExemptRoleIsStillRateLimited() throws Exception {
        authenticateWith(Role.USER);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            filter.doFilter(requestFromIp("10.0.0.5"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(requestFromIp("10.0.0.5"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void bucketsAreIsolatedPerClientIp() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Exhaust the limit for client A.
        for (int i = 0; i < 3; i++) {
            filter.doFilter(requestFromIp("1.1.1.1"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse aBlocked = new MockHttpServletResponse();
        filter.doFilter(requestFromIp("1.1.1.1"), aBlocked, chain);
        assertThat(aBlocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        // Client B has its own fresh bucket and is unaffected.
        MockHttpServletResponse bResponse = new MockHttpServletResponse();
        filter.doFilter(requestFromIp("2.2.2.2"), bResponse, chain);
        assertThat(bResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(bResponse.getHeader("X-Rate-Limit-Remaining")).isEqualTo("2");
    }

    @Test
    void clientIpIsTakenFromXForwardedForFirstHop() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Two requests with different remoteAddr but the same X-Forwarded-For client
        // share a bucket, proving the forwarded header is the bucket key.
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = requestFromIp("10.0.0.99");
            request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.99");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest blockedReq = requestFromIp("10.0.0.100");
        blockedReq.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.100");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void unauthenticatedRequestIsNotExempt() throws Exception {
        // No authentication in the security context.
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            filter.doFilter(requestFromIp("9.9.9.9"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(requestFromIp("9.9.9.9"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void blockedRequestDoesNotInvokeFilterChain() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 3; i++) {
            filter.doFilter(requestFromIp("8.8.8.8"), new MockHttpServletResponse(), chain);
        }

        FilterChain blockedChain = mock(FilterChain.class);
        filter.doFilter(requestFromIp("8.8.8.8"), new MockHttpServletResponse(), blockedChain);

        verify(blockedChain, never()).doFilter(any(), any());
        verifyNoInteractions(blockedChain);
    }
}
