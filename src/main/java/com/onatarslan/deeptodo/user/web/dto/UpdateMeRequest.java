package com.onatarslan.deeptodo.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @NotBlank @Size(max = 100) String displayName
) {
}
