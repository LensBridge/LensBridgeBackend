package com.ibrasoft.lensbridge.security.jwt;

import com.ibrasoft.lensbridge.security.services.AuthenticatedUser;
import com.ibrasoft.lensbridge.security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Access-token revocation in {@link AuthTokenFilter}.
 *
 * <p>A JWT is self-contained, so before {@code tokenVersion} existed a password change revoked
 * every refresh token but left the already-issued 15-minute access token working — a stolen
 * token stayed usable for up to 15 minutes after the victim changed their password precisely to
 * stop it. These tests pin the rule that closes that window: a token whose {@code tv} claim does
 * not match the account's current generation leaves the security context unauthenticated, so the
 * entry point answers 401.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenFilterRevocationTest {

    private static final String EMAIL = "u@example.com";
    private static final String TOKEN = "a.b.c";

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
    @InjectMocks
    private AuthTokenFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    private UserDetails accountAtVersion(long tokenVersion) {
        return new AuthenticatedUser(EMAIL, "hash", true, List.of(), tokenVersion);
    }

    private void tokenPresentsVersion(long presented) {
        ReflectionTestUtils.setField(filter, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
        when(jwtUtils.validateJwtToken(TOKEN)).thenReturn(true);
        when(jwtUtils.getUserNameFromJwtToken(TOKEN)).thenReturn(EMAIL);
        when(jwtUtils.getTokenVersionFromJwtToken(TOKEN)).thenReturn(presented);
    }

    @Test
    void tokenMintedAtTheCurrentGenerationAuthenticates() throws Exception {
        tokenPresentsVersion(3L);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(accountAtVersion(3L));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(EMAIL);
        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenFromBeforeAPasswordChangeIsRejectedDespiteAValidSignature() throws Exception {
        // Signature still verifies and the clock has not run out; the account's generation moved on.
        tokenPresentsVersion(3L);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(accountAtVersion(4L));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // The chain still runs; an unauthenticated context is what makes the entry point answer 401.
        verify(chain).doFilter(request, response);
    }

    @Test
    void principalThatCannotReportAGenerationIsRejected() throws Exception {
        // AuthenticatedUser.tokenVersionOf yields -1 for any other UserDetails, so a principal
        // from some future code path that forgets to carry the version fails closed.
        tokenPresentsVersion(0L);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(
                org.springframework.security.core.userdetails.User
                        .withUsername(EMAIL).password("hash").authorities(List.of()).build());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(bearerRequest(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
