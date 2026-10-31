package com.onatarslan.deeptodo.user.web.dto;

import java.util.UUID;

// Giriş yapmış kullanıcının kendi profili. Parola hash'i ve status dışarı çıkmaz.
public record MeResponse(UUID id, String email, String displayName) {
}
