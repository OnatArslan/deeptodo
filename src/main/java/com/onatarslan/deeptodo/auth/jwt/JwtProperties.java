package com.onatarslan.deeptodo.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "deeptodo.security.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        String keyId,
        String privateKeyLocation,
        String publicKeyLocation
) {
}
