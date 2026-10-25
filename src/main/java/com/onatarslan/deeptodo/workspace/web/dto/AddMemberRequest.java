package com.onatarslan.deeptodo.workspace.web.dto;

import com.onatarslan.deeptodo.workspace.domain.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Invitation yok (spec §11): OWNER sistemde zaten kayıtlı bir kullanıcıyı email ile ekler.
public record AddMemberRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull WorkspaceRole role
) {
}
