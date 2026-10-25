package com.onatarslan.deeptodo.workspace.web.dto;

import com.onatarslan.deeptodo.workspace.domain.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull WorkspaceRole role
) {
}
