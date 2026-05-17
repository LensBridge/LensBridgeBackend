package com.ibrasoft.lensbridge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.token")
@Getter
@Setter
public class AuthTokenProperties {
    private long verificationExpirationMs;
    private long passwordResetExpirationMs;
}
