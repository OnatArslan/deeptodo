package com.onatarslan.deeptodo.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        // Üst sınır argon2'den değil, istek boyutunu makul tutmaktan (argon2'nin bcrypt gibi 72 byte sınırı yok).
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 100) String displayName
) {
}
