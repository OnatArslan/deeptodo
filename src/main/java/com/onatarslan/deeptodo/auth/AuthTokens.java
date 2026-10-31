package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.auth.token.AccessToken;

// Login'in sonucu. Controller ikisini farklı yollarla gönderir: access token gövdede, refresh token cookie'de.
public record AuthTokens(AccessToken accessToken, String refreshToken) {
}
