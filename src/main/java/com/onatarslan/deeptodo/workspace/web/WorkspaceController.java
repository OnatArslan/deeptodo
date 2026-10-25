package com.onatarslan.deeptodo.workspace.web;

import com.onatarslan.deeptodo.auth.CurrentUser;
import com.onatarslan.deeptodo.workspace.WorkspaceService;
import com.onatarslan.deeptodo.workspace.web.dto.CreateWorkspaceRequest;
import com.onatarslan.deeptodo.workspace.web.dto.RenameWorkspaceRequest;
import com.onatarslan.deeptodo.workspace.web.dto.WorkspaceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Controller yetki kararı vermez; kimin istek attığını (token) ve neye (URL) servise iletir.
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateWorkspaceRequest request) {
        return WorkspaceResponse.from(workspaceService.create(CurrentUser.id(jwt), request.name()));
    }

    @GetMapping
    public List<WorkspaceResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        return workspaceService.listMine(CurrentUser.id(jwt)).stream().map(WorkspaceResponse::from).toList();
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId) {
        return WorkspaceResponse.from(workspaceService.get(workspaceId, CurrentUser.id(jwt)));
    }

    @PatchMapping("/{workspaceId}")
    public WorkspaceResponse rename(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID workspaceId,
                                    @Valid @RequestBody RenameWorkspaceRequest request) {
        return WorkspaceResponse.from(workspaceService.rename(workspaceId, CurrentUser.id(jwt), request.name()));
    }

    @PostMapping("/{workspaceId}/archive")
    public WorkspaceResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspaceId) {
        return WorkspaceResponse.from(workspaceService.archive(workspaceId, CurrentUser.id(jwt)));
    }
}
