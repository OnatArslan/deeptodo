package com.onatarslan.deeptodo.workspace.domain;

import java.time.Instant;
import java.util.UUID;

// Üye listesi: üyelik + kullanıcının görünen bilgileri (users tablosundan join).
public record MemberView(UUID id, UUID userId, String email, String displayName, WorkspaceRole role, Instant joinedAt) {
}
