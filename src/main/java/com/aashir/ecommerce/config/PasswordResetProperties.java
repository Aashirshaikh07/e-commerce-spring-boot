package com.aashir.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "password-reset")
public class PasswordResetProperties {
    private int otpLength = 6;
    private int otpExpiryMinutes = 10;
    private int maxAttempts = 5;
    private int resendCooldownSeconds = 60;
    private int tokenExpiryMinutes = 10;
}
