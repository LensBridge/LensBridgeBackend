package com.ibrasoft.lensbridge.security;

import com.ibrasoft.lensbridge.config.RateLimitingFilter;
import com.ibrasoft.lensbridge.config.WebConfig;
import com.ibrasoft.lensbridge.service.agent.http.AuthenticatedDeviceArgumentResolver;
import com.ibrasoft.lensbridge.service.agent.http.DeviceAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The smallest real web application that still has everything deciding the client address:
 * embedded Tomcat configured by Spring Boot from {@code server.forward-headers-strategy} and
 * {@code server.tomcat.remoteip.*} (which installs, or does not install, RemoteIpValve or
 * ForwardedHeaderFilter), and the production {@link WebConfig} registering
 * {@link RateLimitingFilter} on {@code /api/*}. No database or security: neither takes part.
 */
@SpringBootConfiguration
@ImportAutoConfiguration({
        PropertyPlaceholderAutoConfiguration.class,
        ServletWebServerFactoryAutoConfiguration.class,
        EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        JacksonAutoConfiguration.class})
@Import({WebConfig.class, RateLimitingFilter.class, CurrentUserArgumentResolver.class,
        AuthenticatedDeviceArgumentResolver.class, DeviceAuthInterceptor.class, ProxyTestApp.Echo.class})
class ProxyTestApp {

    /** What the application sees of a request after the container and the filters. */
    @RestController
    static class Echo {
        @GetMapping("/api/test/client")
        Map<String, Object> client(HttpServletRequest request) {
            Map<String, Object> seen = new LinkedHashMap<>();
            seen.put("clientAddress", ClientAddress.of(request));
            seen.put("scheme", request.getScheme());
            seen.put("secure", request.isSecure());
            seen.put("serverName", request.getServerName());
            seen.put("serverPort", request.getServerPort());
            return seen;
        }
    }
}
