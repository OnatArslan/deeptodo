package com.onatarslan.deeptodo.workspace;

import com.onatarslan.deeptodo.common.error.BusinessRuleViolationException;
import com.onatarslan.deeptodo.common.error.ForbiddenOperationException;
import com.onatarslan.deeptodo.common.error.ResourceNotFoundException;
import com.onatarslan.deeptodo.user.UserService;
import com.onatarslan.deeptodo.workspace.domain.MemberView;
import com.onatarslan.deeptodo.workspace.domain.MyWorkspaceView;
import com.onatarslan.deeptodo.workspace.domain.Workspace;
import com.onatarslan.deeptodo.workspace.domain.WorkspaceMember;
import com.onatarslan.deeptodo.workspace.domain.WorkspaceMemberRepository;
import com.onatarslan.deeptodo.workspace.domain.WorkspaceRepository;
import com.onatarslan.deeptodo.workspace.domain.WorkspaceRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

// Workspace ve üyelik use-case'leri + yetki kuralları (spec §9).
//
// Authorization burada, SecurityConfig'te değil. Sebep: kural URL'ye değil veriye bağlı
// ("bu kullanıcı BU workspace'in OWNER'ı mı?"). Filter zinciri yalnız "kimliği doğrulanmış mı?" sorusunu cevaplar.
//
// Her işlem iki soruyla başlar:
//   1. Üye mi?  Değilse 404 — workspace'in var olduğu bile söylenmez.
//   2. Rolü yetiyor mu?  Üye ama OWNER değilse 403 — kaynağı zaten görebiliyor, saklanacak bir şey yok.
@Service
public class WorkspaceService {

    private static final String WORKSPACE_NOT_FOUND = "workspace not found";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserService userService;
    private final Clock clock;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceMemberRepository memberRepository,
                            UserService userService,
                            Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userService = userService;
        this.clock = clock;
    }

    // ─── Workspace ───────────────────────────────────────────────────────────

    // Spec §7.2 creation invariant: workspace + ilk OWNER aynı transaction'da. Biri başarısız olursa ikisi de geri alınır;
    // OWNER'sız bir workspace hiçbir an DB'de görünmez.
    @Transactional
    public MyWorkspaceView create(UUID userId, String name) {
        Workspace workspace = workspaceRepository.save(Workspace.create(name, userId));
        memberRepository.save(WorkspaceMember.founder(workspace.getId(), userId, clock.instant()));
        return view(workspace, WorkspaceRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<MyWorkspaceView> listMine(UUID userId) {
        return workspaceRepository.findAllForMember(userId);
    }

    @Transactional(readOnly = true)
    public MyWorkspaceView get(UUID workspaceId, UUID userId) {
        WorkspaceMember me = requireMember(workspaceId, userId);
        return view(findWorkspace(workspaceId), me.getRole());
    }

    @Transactional
    public MyWorkspaceView rename(UUID workspaceId, UUID userId, String newName) {
        requireOwner(requireMember(workspaceId, userId));
        Workspace workspace = findWorkspace(workspaceId);
        requireActive(workspace);
        workspace.rename(newName);
        return view(workspace, WorkspaceRole.OWNER);
    }

    // İdempotent: zaten archived ise aynı sonucu döner.
    @Transactional
    public MyWorkspaceView archive(UUID workspaceId, UUID userId) {
        requireOwner(requireMember(workspaceId, userId));
        Workspace workspace = findWorkspace(workspaceId);
        workspace.archive();
        return view(workspace, WorkspaceRole.OWNER);
    }

    // ─── Members ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MemberView> listMembers(UUID workspaceId, UUID userId) {
        requireMember(workspaceId, userId);
        return memberRepository.findMembers(workspaceId);
    }

    @Transactional
    public MemberView addMember(UUID workspaceId, UUID actorId, String email, WorkspaceRole role) {
        requireOwner(requireMember(workspaceId, actorId));
        requireActive(findWorkspace(workspaceId));

        var user = userService.findActiveByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, user.getId())) {
            throw new BusinessRuleViolationException("user is already a member");
        }

        WorkspaceMember member = WorkspaceMember.added(workspaceId, user.getId(), role, clock.instant(), actorId);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // Ön kontrol ile insert arasına giren eşzamanlı ekleme: UNIQUE (workspace_id, user_id) yakalar.
            throw new BusinessRuleViolationException("user is already a member");
        }
        return new MemberView(member.getId(), user.getId(), user.getEmail(), user.getDisplayName(),
                member.getRole(), member.getJoinedAt());
    }

    @Transactional
    public void changeRole(UUID workspaceId, UUID actorId, UUID memberId, WorkspaceRole newRole) {
        Workspace workspace = lockWorkspace(workspaceId);
        requireOwner(requireMember(workspaceId, actorId));
        requireActive(workspace);

        WorkspaceMember target = findMember(workspaceId, memberId);
        if (target.isOwner() && newRole == WorkspaceRole.MEMBER) {
            requireAnotherOwner(workspaceId);
        }
        target.changeRole(newRole);
    }

    // OWNER herkesi çıkarabilir; bir üye kendini çıkarabilir (workspace'ten ayrılmak).
    @Transactional
    public void removeMember(UUID workspaceId, UUID actorId, UUID memberId) {
        lockWorkspace(workspaceId);
        WorkspaceMember actor = requireMember(workspaceId, actorId);
        WorkspaceMember target = findMember(workspaceId, memberId);

        boolean leavingSelf = target.getUserId().equals(actorId);
        if (!leavingSelf) {
            requireOwner(actor);
        }
        if (target.isOwner()) {
            requireAnotherOwner(workspaceId);
        }
        // Not: TodoAssignee eklendiğinde, üyelik silinmeden önce o üyeye bağlı atamalar burada çözülecek (spec §7.8).
        memberRepository.delete(target);
    }

    // ─── Yetki ve invariant yardımcıları ─────────────────────────────────────

    private WorkspaceMember requireMember(UUID workspaceId, UUID userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(WORKSPACE_NOT_FOUND));
    }

    private static void requireOwner(WorkspaceMember member) {
        if (!member.isOwner()) {
            throw new ForbiddenOperationException("only an OWNER can do this");
        }
    }

    private static void requireActive(Workspace workspace) {
        if (workspace.isArchived()) {
            throw new BusinessRuleViolationException("workspace is archived");
        }
    }

    // Son OWNER invariant'ı (spec §7.3): bu işlemden sonra en az bir OWNER kalmalı.
    // Sayım bir SQL count sorgusu; lockWorkspace sayesinde başka bir transaction araya giremez.
    private void requireAnotherOwner(UUID workspaceId) {
        if (memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw new BusinessRuleViolationException("workspace must keep at least one OWNER");
        }
    }

    // Rol / üyelik değiştiren işlemler önce workspace satırını kilitler (SELECT ... FOR UPDATE).
    // Neden kilit, neden önce: iki OWNER (A ve B) aynı anda birbirini MEMBER'a düşürmeye çalışsın.
    // Kilit olmadan ikisi de "2 OWNER var" görür, ikisi de başarılı olur, workspace 0 OWNER'a düşer (write skew).
    // Kilitle ikinci istek bekler; birincisi commit edince kendi üyeliğini ve OWNER sayısını güncel haliyle okur.
    // Kilidin üyelik okumalarından ÖNCE alınması önemli: önce okunan bir entity persistence context'te kalır
    // ve sonraki sorgular onu yenilemez; kilitten sonra okunan veri ise güncel commit'i görür.
    private Workspace lockWorkspace(UUID workspaceId) {
        return workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException(WORKSPACE_NOT_FOUND));
    }

    private Workspace findWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException(WORKSPACE_NOT_FOUND));
    }

    // id + workspaceId birlikte: başka bir workspace'in üyelik id'si bu URL'den bulunamaz.
    private WorkspaceMember findMember(UUID workspaceId, UUID memberId) {
        return memberRepository.findByIdAndWorkspaceId(memberId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("member not found"));
    }

    private static MyWorkspaceView view(Workspace workspace, WorkspaceRole myRole) {
        return new MyWorkspaceView(workspace.getId(), workspace.getName(), workspace.getStatus(), myRole,
                workspace.getCreatedAt());
    }
}
