package com.ibrasoft.lensbridge.config;

import java.util.List;

import com.ibrasoft.lensbridge.security.CurrentUserArgumentResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${frontend.baseurl}")
    String frontendBaseUrl;

    @Value("${musallahboard.baseurl}")
    String musallahBoardBaseUrl;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private CurrentUserArgumentResolver currentUserArgumentResolver;

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
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendBaseUrl, musallahBoardBaseUrl)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true); 
    }
}
