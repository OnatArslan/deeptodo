package com.onatarslan.deeptodo.auth;

import java.time.Instant;
import java.util.UUID;

// AuthService'in web katmanına verdiği sonuç: access token gövdeye, refresh token cookie'ye gidecek.
public record AuthTokens(
        UUID userId,
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
    @Override
    public String toString() {
        // Token'lar log'a düşmesin.
        return "AuthTokens[userId=" + userId + "]";
    }
}
