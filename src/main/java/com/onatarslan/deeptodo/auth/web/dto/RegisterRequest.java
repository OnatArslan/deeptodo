package com.onatarslan.deeptodo.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,
        // max = 64: 5. mesajdaki bcrypt 72 byte sınırı yüzünden
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 100) String displayName
) {
    // Record'un otomatik toString()'i şifreyi düz metin olarak yazdırır (aşağıdaki tuzaklara bak)
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + "]";
    }
}
