package com.onatarslan.deeptodo.workspace.web;

import com.onatarslan.deeptodo.auth.CurrentUser;
import com.onatarslan.deeptodo.workspace.WorkspaceService;
import com.onatarslan.deeptodo.workspace.web.dto.AddMemberRequest;
import com.onatarslan.deeptodo.workspace.web.dto.ChangeRoleRequest;
import com.onatarslan.deeptodo.workspace.web.dto.MemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {

    private final WorkspaceService workspaceService;

    public WorkspaceMemberController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<MemberResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId) {
        return workspaceService.listMembers(workspaceId, CurrentUser.id(jwt)).stream().map(MemberResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse add(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID workspaceId,
                              @Valid @RequestBody AddMemberRequest request) {
        return MemberResponse.from(workspaceService.addMember(workspaceId, CurrentUser.id(jwt), request.email(), request.role()));
    }

    @PatchMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRole(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID workspaceId,
                           @PathVariable UUID memberId,
                           @Valid @RequestBody ChangeRoleRequest request) {
        workspaceService.changeRole(workspaceId, CurrentUser.id(jwt), memberId, request.role());
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId, @PathVariable UUID memberId) {
        workspaceService.removeMember(workspaceId, CurrentUser.id(jwt), memberId);
    }
}
