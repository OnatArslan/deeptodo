package com.onatarslan.deeptodo.auth.web.dto;

import java.util.UUID;

public record RegisterResponse(UUID id, String email, String displayName) {
}
