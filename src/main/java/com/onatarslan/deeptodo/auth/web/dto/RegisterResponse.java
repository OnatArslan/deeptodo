package com.onatarslan.deeptodo.auth.web.dto;

import java.util.UUID;

// Register sonrası otomatik login: id + access token gövdede, refresh token Set-Cookie'de.
public record RegisterResponse(
        UUID id,
        String accessToken,
        String tokenType,
        long expiresIn
) {
    @Override
    public String toString() {
        return "RegisterResponse[id=" + id + "]";
    }
}
