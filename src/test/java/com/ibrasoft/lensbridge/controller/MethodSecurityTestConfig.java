package com.ibrasoft.lensbridge.controller;

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
 */
@TestConfiguration
@EnableMethodSecurity
class MethodSecurityTestConfig {

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
