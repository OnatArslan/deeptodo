package com.onatarslan.deeptodo.auth.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// application.yaml → deeptodo.security.refresh-token.*
@Validated
@ConfigurationProperties("deeptodo.security.refresh-token")
public record RefreshTokenProperties(
        @NotNull Duration ttl,
        // Cookie'nin Secure bayrağı: tarayıcı cookie'yi yalnız HTTPS üzerinden gönderir. Local HTTP'de true olursa
        // tarayıcı cookie'yi hiç geri göndermez; bu yüzden local'de false, production'da true (E4).
        boolean cookieSecure
) {
}
