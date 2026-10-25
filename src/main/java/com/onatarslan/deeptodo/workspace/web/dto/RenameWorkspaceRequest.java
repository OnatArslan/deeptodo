package com.onatarslan.deeptodo.workspace.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameWorkspaceRequest(
        @NotBlank @Size(max = 120) String name
) {
}
