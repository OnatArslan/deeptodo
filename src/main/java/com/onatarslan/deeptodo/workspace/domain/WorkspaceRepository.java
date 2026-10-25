package com.onatarslan.deeptodo.workspace.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    // Query-level scoping (spec §9): kullanıcının yalnız üyesi olduğu workspace'ler döner.
    // Filtre Java'da değil SQL'de; başkasının workspace'i hiç yüklenmez.
    @Query("""
            select new com.onatarslan.deeptodo.workspace.domain.MyWorkspaceView(w.id, w.name, w.status, m.role, w.createdAt)
              from Workspace w
              join WorkspaceMember m on m.workspaceId = w.id
             where m.userId = :userId
             order by w.createdAt
            """)
    List<MyWorkspaceView> findAllForMember(@Param("userId") UUID userId);

    // SELECT ... FOR UPDATE: bu workspace'in üyelik değişikliklerini sıraya sokar.
    // Son OWNER invariant'ı için gerekli (spec §7.3): iki OWNER aynı anda birbirini düşürmeye çalışırsa
    // ikincisi bu satırda bekler, birincisi commit edince OWNER sayısını güncel haliyle görür.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workspace w where w.id = :id")
    Optional<Workspace> findByIdForUpdate(@Param("id") UUID id);
}
