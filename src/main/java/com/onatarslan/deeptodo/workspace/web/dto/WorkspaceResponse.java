package com.onatarslan.deeptodo.workspace.web.dto;

import com.onatarslan.deeptodo.workspace.domain.MyWorkspaceView;
import com.onatarslan.deeptodo.workspace.domain.WorkspaceRole;
import com.onatarslan.deeptodo.workspace.domain.WorkspaceStatus;

import java.time.Instant;
import java.util.UUID;

// myRole: istemci hangi butonları göstereceğini bilsin (ör. MEMBER'a "archive" gösterme).
// Bu yalnız bir ipucu; asıl kontrol her istekte serviste yapılır.
public record WorkspaceResponse(UUID id, String name, WorkspaceStatus status, WorkspaceRole myRole, Instant createdAt) {

    public static WorkspaceResponse from(MyWorkspaceView view) {
        return new WorkspaceResponse(view.id(), view.name(), view.status(), view.myRole(), view.createdAt());
    }
}
