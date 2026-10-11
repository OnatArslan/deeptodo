create table users
(
    id            uuid         not null,
    email         varchar(320) not null,
    password_hash varchar(255) not null,
    display_name  varchar(100) not null,
    status        varchar(20)  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    version       bigint       not null,

    constraint pk_users primary key (id),
    constraint uq_users_email unique (email),
    constraint ck_users_status check ( status in ('ACTIVE', 'DEACTIVATED') ),
    constraint ck_users_email_lowercase check ( email = lower(email) ),
    constraint ck_users_display_name_not_blank check ( display_name ~ '\S' )
);

create table workspaces
(
    id                 uuid         not null,
    name               varchar(120) not null,
    status             varchar(20)  not null,
    created_by_user_id uuid         not null,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,

    constraint pk_workspaces primary key (id),
    constraint ck_workspaces_status check ( status in ('ACTIVE', 'ARCHIVED') ),
    constraint ck_workspaces_name_not_blank check ( name ~ '\S' ),
    constraint fk_workspaces_created_by_user foreign key (created_by_user_id) references users (id)
);
