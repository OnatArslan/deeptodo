package com.onatarslan.deeptodo.workspace.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    // Her workspace isteğinin ilk adımı: "bu kullanıcı bu workspace'in üyesi mi, rolü ne?"
    // uq_workspace_members_workspace_user index'i bu sorguyu karşılar.
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    // Kaynak id'si + workspace id'si birlikte (spec §9): başka bir workspace'in üyelik id'si URL'ye yazılsa bile bulunmaz.
    Optional<WorkspaceMember> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);

    @Query("""
            select new com.onatarslan.deeptodo.workspace.domain.MemberView(m.id, m.userId, u.email, u.displayName, m.role, m.joinedAt)
              from WorkspaceMember m
              join com.onatarslan.deeptodo.user.domain.User u on u.id = m.userId
             where m.workspaceId = :workspaceId
             order by m.joinedAt
            """)
    List<MemberView> findMembers(@Param("workspaceId") UUID workspaceId);
}
