-- Spec §7.3. User ↔ Workspace ilişkisinin açık entity'si; rol burada tutulur (JWT'de değil).
create table workspace_members
(
    id               uuid        not null,
    workspace_id     uuid        not null,
    user_id          uuid        not null,
    role             varchar(20) not null,
    joined_at        timestamptz not null,
    added_by_user_id uuid,
    updated_at       timestamptz not null,
    version          bigint      not null,

    constraint pk_workspace_members primary key (id),
    -- Aynı kullanıcı aynı workspace'te bir kez üye olabilir. Bu UNIQUE'in index'i
    -- (workspace_id, user_id) ile yapılan üyelik kontrolünü ve workspace_id'ye göre listelemeyi de karşılar.
    constraint uq_workspace_members_workspace_user unique (workspace_id, user_id),
    constraint fk_workspace_members_workspace foreign key (workspace_id) references workspaces (id),
    constraint fk_workspace_members_user foreign key (user_id) references users (id),
    constraint fk_workspace_members_added_by foreign key (added_by_user_id) references users (id),
    constraint ck_workspace_members_role check (role in ('OWNER', 'MEMBER'))
);

-- "Üyesi olduğum workspace'ler" sorgusu user_id ile başlar; UNIQUE index'in ilk kolonu workspace_id olduğu için onu kullanamaz.
create index idx_workspace_members_user_id on workspace_members (user_id);
