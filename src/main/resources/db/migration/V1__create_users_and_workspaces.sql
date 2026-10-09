create table users
(
    id           uuid primary key,
    email        varchar(320) not null,
    display_name varchar(100) not null,
    status       varchar(20)  not null,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),

    constraint uq_users_email unique (email),
    constraint ck_users_status check ( status in ('ACTIVE', 'DEACTIVATED') )
);

create table workspaces
(
    id                 uuid primary key,
    name               varchar(120) not null,
    status             varchar(20)  not null,
    created_by_user_id uuid         not null,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),

    constraint ck_workspaces_status check ( status in ('ACTIVE', 'ARCHIVED') ),
    constraint fk_workspaces_created_by_user foreign key (created_by_user_id) references users (id)
);