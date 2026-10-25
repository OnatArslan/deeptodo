package com.onatarslan.deeptodo.workspace.domain;

import com.onatarslan.deeptodo.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

// Spec §7.2. Hard delete yok; archive edilir. Archived workspace'te yeni üye vb. oluşturulmaz.
@Entity
@Table(name = "workspaces")
public class Workspace extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkspaceStatus status;

    // Audit alanı: scalar UUID (spec §7.1 notu). FK DB'de; navigasyon gerekmiyor.
    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    // For JPA
    protected Workspace() {
    }

    private Workspace(String name, UUID createdByUserId) {
        this.name = requireName(name);
        this.createdByUserId = createdByUserId;
        this.status = WorkspaceStatus.ACTIVE;
    }

    public static Workspace create(String name, UUID createdByUserId) {
        return new Workspace(name, createdByUserId);
    }

    public void rename(String newName) {
        this.name = requireName(newName);
    }

    public void archive() {
        this.status = WorkspaceStatus.ARCHIVED;
    }

    public boolean isArchived() {
        return status == WorkspaceStatus.ARCHIVED;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("workspace name can not be null or blank");
        }
        return name.strip();
    }

    public String getName() {
        return name;
    }

    public WorkspaceStatus getStatus() {
        return status;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }
}
