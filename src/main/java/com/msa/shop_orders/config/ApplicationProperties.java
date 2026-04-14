package com.msa.shop_orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
        Security security,
        Otp otp
) {
    public record Security(
            String accessTokenSecret,
            long accessTokenValiditySeconds,
            long refreshTokenValiditySeconds
    ) {
    }

    public record Otp(
            String hardcodedValue,
            String hardcodedAadhaarOtp,
            long expirySeconds
    ) {
    }
}
