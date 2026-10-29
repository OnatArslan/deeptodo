package com.onatarslan.deeptodo.auth.web.dto;

import java.util.UUID;

// Şimdilik yalnız "kim olduğun doğrulandı". R4'te access token, R7'de refresh token cookie'si eklenir.
public record LoginResponse(UUID userId, String email) {
}
