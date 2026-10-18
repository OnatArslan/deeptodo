package com.onatarslan.deeptodo.auth.web;

import com.onatarslan.deeptodo.auth.refresh.RefreshTokenProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Refresh token yalnız cookie'de taşınır (spec §10): JavaScript okuyamaz (HttpOnly),
// yalnız /api/auth isteklerine gider (Path), başka siteden gelen isteklere eklenmez (SameSite=Strict).
// SameSite=Strict, CSRF kapalıyken /refresh ve /logout'u cross-site isteklerden koruyan şey.
@Component
public class RefreshTokenCookies {

    private static final String PATH = "/api/auth";

    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenCookies(RefreshTokenProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String name() {
        return properties.cookieName();
    }

    public ResponseCookie issue(String rawToken, Instant expiresAt) {
        return base(rawToken)
                .maxAge(Duration.between(clock.instant(), expiresAt))
                .build();
    }

    // Max-Age=0: tarayıcı cookie'yi hemen siler.
    public ResponseCookie clear() {
        return base("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
