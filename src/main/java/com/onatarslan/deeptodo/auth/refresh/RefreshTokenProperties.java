package com.onatarslan.deeptodo.auth.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "deeptodo.security.refresh-token")
public record RefreshTokenProperties(
        Duration ttl,
        String cookieName,
        // Local HTTP'de false; production'da (HTTPS) mutlaka true olmalı (spec §10).
        boolean cookieSecure
) {
}
