package com.onatarslan.deeptodo.workspace.web.dto;

import com.onatarslan.deeptodo.workspace.domain.MemberView;
import com.onatarslan.deeptodo.workspace.domain.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

// id: üyeliğin id'si (PATCH / DELETE /members/{memberId} bunu bekler), userId değil.
public record MemberResponse(UUID id, UUID userId, String email, String displayName, WorkspaceRole role, Instant joinedAt) {

    public static MemberResponse from(MemberView view) {
        return new MemberResponse(view.id(), view.userId(), view.email(), view.displayName(), view.role(), view.joinedAt());
    }
}
