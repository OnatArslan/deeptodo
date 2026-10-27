package com.onatarslan.deeptodo.auth.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName
) {

    // bcrypt yalnız ilk 72 BYTE'ı kullanır; Spring Security daha uzununu reddeder (IllegalArgumentException → 500).
    // @Size karakter sayar: "ğ" gibi bir karakter UTF-8'de 2 byte, yani 72 karakter 72 byte'ı aşabilir.
    @AssertTrue(message = "password must be at most 72 bytes in UTF-8")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
