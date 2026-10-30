package com.onatarslan.deeptodo.auth.web.dto;

// OAuth2 token cevabının (RFC 6749 §5.1: access_token, token_type, expires_in) camelCase hali.
// tokenType "Bearer": istemci token'ı Authorization: Bearer <accessToken> olarak gönderir (R5).
// Kullanıcı id'si ayrıca dönmez; token'ın sub claim'inde. Refresh token R7'de cookie olarak eklenir.
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
