package com.onatarslan.deeptodo.workspace.domain;

// Workspace'e özel rol (spec §9). Global bir rol değildir: aynı kullanıcı bir workspace'te OWNER,
// başka birinde MEMBER olabilir. Bu yüzden JWT'ye yazılmaz, her istekte workspace_members'tan okunur.
public enum WorkspaceRole {
    OWNER,
    MEMBER
}
