package com.ibrasoft.lensbridge.config;

import java.util.List;

import com.ibrasoft.lensbridge.security.CurrentUserArgumentResolver;
import com.ibrasoft.lensbridge.service.agent.http.AuthenticatedDeviceArgumentResolver;
import com.ibrasoft.lensbridge.service.agent.http.DeviceAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC wiring. CORS is deliberately not configured here: it is declared once, in
 * {@code WebSecurityConfig.corsConfigurationSource}, and Spring Security's CorsFilter answers
 * before MVC ever sees the request. A second mapping here was dead weight that had already
 * drifted (it omitted PATCH).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private CurrentUserArgumentResolver currentUserArgumentResolver;

    @Autowired
    private AuthenticatedDeviceArgumentResolver authenticatedDeviceArgumentResolver;

    @Autowired
    private DeviceAuthInterceptor deviceAuthInterceptor;

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration() {
        FilterRegistrationBean<RateLimitingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(rateLimitingFilter);
        registrationBean.addUrlPatterns("/api/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
        resolvers.add(authenticatedDeviceArgumentResolver);
    }

    /** Device-authenticated agent endpoints; see {@code @AuthenticatedDevice}. */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(deviceAuthInterceptor).addPathPatterns("/api/agent/**");
    }
}
