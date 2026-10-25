package com.onatarslan.deeptodo.workspace.domain;

import java.time.Instant;
import java.util.UUID;

// "Üyesi olduğum workspace'ler" sorgusunun sonucu: workspace + bu kullanıcının oradaki rolü.
public record MyWorkspaceView(UUID id, String name, WorkspaceStatus status, WorkspaceRole myRole, Instant createdAt) {
}
