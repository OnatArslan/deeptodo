package com.onatarslan.deeptodo.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Login'de email formatı (@Email) ve parola alt sınırı kontrol edilmez: kurala uymayan bir girdi zaten
// hiçbir hesapla eşleşmez, sonuç aynı 401 olur. Üst sınırlar yalnız istek boyutu için (register ile aynı).
public record LoginRequest(
        @NotBlank @Size(max = 320) String email,
        @NotBlank @Size(max = 128) String password
) {
}
