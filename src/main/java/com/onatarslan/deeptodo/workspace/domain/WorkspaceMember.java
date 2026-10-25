package com.onatarslan.deeptodo.workspace.domain;

import com.onatarslan.deeptodo.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

// Spec §7.3. User ↔ Workspace ilişkisinin açık entity'si (@ManyToMany yok).
//
// İlişki için sorduğumuz dört soru:
//   FK nerede?          workspace_members (workspace_id, user_id)
//   Cardinality?        bir workspace'in çok üyesi, bir kullanıcının çok üyeliği
//   Lifecycle sahibi?   workspace feature'ı
//   Navigasyon gerekli? Hayır: üyelik kontrolü ve listeleme sorgu ile yapılıyor → scalar UUID.
//
// AuditableEntity'den türemez: tabloda created_at yok, yerine joined_at var (BaseEntity hiyerarşisi kararı).
@Entity
@Table(name = "workspace_members")
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceMember extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private WorkspaceRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    // Workspace'i oluşturan ilk OWNER için null (kimse eklemedi).
    @Column(name = "added_by_user_id", updatable = false)
    private UUID addedByUserId;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Spec §7.3: eşzamanlı rol değişikliklerinde kayıp güncellemeyi engeller.
    // Son OWNER invariant'ını tek başına çözmez; onun için WorkspaceService workspace satırını kilitler.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // For JPA
    protected WorkspaceMember() {
    }

    private WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role, Instant joinedAt, UUID addedByUserId) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.addedByUserId = addedByUserId;
    }

    // Workspace oluşturulurken: ilk üye her zaman OWNER (spec §7.2 creation invariant).
    public static WorkspaceMember founder(UUID workspaceId, UUID userId, Instant now) {
        return new WorkspaceMember(workspaceId, userId, WorkspaceRole.OWNER, now, null);
    }

    public static WorkspaceMember added(UUID workspaceId, UUID userId, WorkspaceRole role, Instant now, UUID addedByUserId) {
        return new WorkspaceMember(workspaceId, userId, role, now, addedByUserId);
    }

    public void changeRole(WorkspaceRole newRole) {
        this.role = newRole;
    }

    public boolean isOwner() {
        return role == WorkspaceRole.OWNER;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
