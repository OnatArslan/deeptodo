alter table users
    add column version bigint not null default 1;

alter table workspaces
    add column version bigint not null default 1;

