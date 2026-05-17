package com.ibrasoft.lensbridge.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * End-to-end security matrix test that boots the FULL application context (default dev
 * profile, SQLite) so it exercises the REAL {@link WebSecurityConfig} filter chain,
 * {@code AuthTokenFilter}, {@code AuthEntryPointJwt} and method-level {@code @PreAuthorize}
 * annotations together -- NOT a sliced {@code @WebMvcTest}.
 *
 * <p>The assertions intentionally only distinguish between the three security-relevant
 * outcomes:
 * <ul>
 *   <li><b>401</b> -- anonymous access to a protected resource (entry point fired)</li>
 *   <li><b>403</b> -- authenticated but lacking the required role (access denied)</li>
 *   <li><b>allowed</b> -- request passed security; downstream 200/400/404/500 from
 *       missing test data is acceptable, we only assert it is NOT 401 and NOT 403</li>
 * </ul>
 *
 * <p>Note on swagger/openapi paths: {@code WebSecurityConfig} permits
 * {@code /swagger-ui.html} explicitly and {@code /v3/api-docs/**} as a prefix. The bare
 * {@code /v3/api-docs} path is not covered by the {@code /**} matcher, so the
 * permitAll-reachable check uses {@code /v3/api-docs/swagger-config} which is covered.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityFilterChainMatrixTest {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    @Autowired
    private MockMvc mockMvc;

    private int status(HttpMethod method, String path) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path);
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            builder = builder.contentType("application/json").content("{}");
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        return result.getResponse().getStatus();
    }

    // --- Public paths: anonymous access must NOT be rejected with 401 -----------------

    static Stream<Arguments> publicPaths() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/actuator/health"),
                Arguments.of(HttpMethod.POST, "/api/auth/signin"),
                Arguments.of(HttpMethod.GET, "/api/gallery/approved"),
                Arguments.of(HttpMethod.GET, "/api/events"),
                Arguments.of(HttpMethod.GET, "/api/musallah/config"),
                Arguments.of(HttpMethod.POST, "/api/agent/enroll"),
                Arguments.of(HttpMethod.GET, "/swagger-ui.html"),
                Arguments.of(HttpMethod.GET, "/v3/api-docs/swagger-config"));
    }

    @ParameterizedTest(name = "[{index}] {0} {1} reachable anonymously")
    @MethodSource("publicPaths")
    @WithAnonymousUser
    @DisplayName("Public path prefixes are reachable anonymously (not 401)")
    void publicPathsAreNotUnauthorized(HttpMethod method, String path) throws Exception {
        assertThat(status(method, path))
                .as("%s %s should be publicly reachable (not 401)", method, path)
                .isNotEqualTo(UNAUTHORIZED);
    }

    // --- Protected paths: anonymous access must be 401 -------------------------------

    static Stream<Arguments> protectedPaths() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/user/profile"),
                Arguments.of(HttpMethod.GET, "/api/upload/limits"),
                Arguments.of(HttpMethod.GET, "/api/admin/uploads"),
                Arguments.of(HttpMethod.GET, "/api/admin/board/configs"));
    }

    @ParameterizedTest(name = "[{index}] {0} {1} -> 401 when anonymous")
    @MethodSource("protectedPaths")
    @WithAnonymousUser
    @DisplayName("Protected paths return 401 when anonymous")
    void protectedPathsReturnUnauthorizedWhenAnonymous(HttpMethod method, String path) throws Exception {
        assertThat(status(method, path))
                .as("%s %s should require authentication", method, path)
                .isEqualTo(UNAUTHORIZED);
    }

    // --- Role: USER ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] USER {0} {1} -> {2}")
    @MethodSource("userRoleCases")
    @WithMockUser(roles = "USER")
    @DisplayName("USER role: own profile allowed, admin endpoints forbidden")
    void userRoleMatrix(HttpMethod method, String path, boolean expectForbidden) throws Exception {
        int code = status(method, path);
        if (expectForbidden) {
            assertThat(code).as("%s %s should be 403 for USER", method, path).isEqualTo(FORBIDDEN);
        } else {
            assertThat(code).as("%s %s should be allowed for USER (not 401/403)", method, path)
                    .isNotEqualTo(UNAUTHORIZED).isNotEqualTo(FORBIDDEN);
        }
    }

    static Stream<Arguments> userRoleCases() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/user/profile", false),
                Arguments.of(HttpMethod.GET, "/api/admin/uploads", true),
                Arguments.of(HttpMethod.GET, "/api/admin/board/configs", true));
    }

    // --- Role: ADMIN -----------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] ADMIN {0} {1} -> {2}")
    @MethodSource("adminRoleCases")
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN role: admin uploads allowed, board configs forbidden (ROOT only)")
    void adminRoleMatrix(HttpMethod method, String path, boolean expectForbidden) throws Exception {
        int code = status(method, path);
        if (expectForbidden) {
            assertThat(code).as("%s %s should be 403 for ADMIN", method, path).isEqualTo(FORBIDDEN);
        } else {
            assertThat(code).as("%s %s should be allowed for ADMIN (not 401/403)", method, path)
                    .isNotEqualTo(UNAUTHORIZED).isNotEqualTo(FORBIDDEN);
        }
    }

    static Stream<Arguments> adminRoleCases() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/admin/uploads", false),
                Arguments.of(HttpMethod.GET, "/api/admin/board/configs", true));
    }

    // --- Role: ROOT ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] ROOT {0} {1} allowed")
    @MethodSource("rootRoleCases")
    @WithMockUser(roles = "ROOT")
    @DisplayName("ROOT role: board configs allowed (not 401/403)")
    void rootRoleMatrix(HttpMethod method, String path) throws Exception {
        assertThat(status(method, path))
                .as("%s %s should be allowed for ROOT (not 401/403)", method, path)
                .isNotEqualTo(UNAUTHORIZED).isNotEqualTo(FORBIDDEN);
    }

    static Stream<Arguments> rootRoleCases() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/admin/board/configs"));
    }
}
