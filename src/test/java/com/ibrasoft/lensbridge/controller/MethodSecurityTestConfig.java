package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.config.ClientIpProperties;
import com.ibrasoft.lensbridge.security.ClientIpResolver;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Minimal security setup for {@code @WebMvcTest} permission tests.
 *
 * <p>Production {@link com.ibrasoft.lensbridge.security.WebSecurityConfig} relies on JWT beans and
 * {@code @Value} properties that are out of scope for slice tests. This config replicates only what
 * the permission tests need: {@link EnableMethodSecurity} so class-level {@code @PreAuthorize} is
 * enforced (yielding 403 for wrong roles), and a filter chain that requires authentication for all
 * requests (yielding 401 for anonymous, matching the production entry-point behaviour). CSRF is
 * disabled, mirroring production.
 *
 * <p>It also supplies {@link ClientIpResolver}. {@code @WebMvcTest} picks up
 * {@link com.ibrasoft.lensbridge.config.RateLimitingFilter} because it is a servlet filter,
 * but not the resolver it depends on, which lives outside the slice. Without this bean every
 * importer of this config fails to start its context. No trusted proxies are configured, so
 * the resolver ignores {@code X-Forwarded-For} entirely and keys on the peer address --
 * the safe default, and the right one for a slice test with no proxy in front of it.
 */
@TestConfiguration
@EnableMethodSecurity
class MethodSecurityTestConfig {

    @Bean
    ClientIpResolver testClientIpResolver() {
        // @PostConstruct on the resolver is honoured by the container for @Bean instances,
        // so the trusted-proxy matchers are initialised without calling the (package-private)
        // initialiser from here.
        return new ClientIpResolver(new ClientIpProperties());
    }

    @Bean
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
